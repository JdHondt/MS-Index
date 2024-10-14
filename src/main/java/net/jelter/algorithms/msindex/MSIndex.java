package net.jelter.algorithms.msindex;

import net.jelter.algorithms.Algorithm;
import net.jelter.algorithms.msindex.segmentation.AdhocSegment;
import net.jelter.algorithms.msindex.segmentation.SegmentMethods;
import net.jelter.io.DataManager;
import net.jelter.utils.*;
import net.jelter.utils.rtreemulti.*;
import net.jelter.utils.rtreemulti.geometry.Geometry;
import net.jelter.utils.rtreemulti.internal.LeafDefault;
import net.jelter.utils.rtreemulti.internal.NonLeafDefault;

import java.io.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static net.jelter.utils.Parameters.*;

public class MSIndex extends Algorithm {
    final KMeans[] kMeans = new KMeans[dimensions];
    RTree<CandidateSegment, Geometry> tree;
    private final int treeDimensions = fourierLength();

//    Boolean indicating if we want to use the index or not (in 2nd case we stop at root)
    private boolean buildTree = true;

    public MSIndex(boolean buildTree) {
        this.buildTree = buildTree;

        DFTUtils.computeKMeansClusters(kMeans);
    }

//    private List<Leaf> getLeafs(Node node) {
//        if (node instanceof Leaf) {
//            return Collections.singletonList((Leaf) node);
//        } else {
//            ArrayList<Leaf> leafs = new ArrayList<>();
//            NonLeaf nonLeaf = (NonLeaf) node;
//            for (Object child : nonLeaf.children()) {
//                leafs.addAll(getLeafs((Node) child));
//            }
//            return leafs;
//        }
//    }

    @Override
    public String getIndexPath() {
//        Make folder index_caches if it does not exist
        File folder = new File("index_caches");
        if (!folder.exists()) {
            folder.mkdir();
        }

        return "index_caches/" + String.join("_",
                algorithmType.toString(),
                datasetName.toString(),
                String.valueOf(N),
                String.valueOf(maxM),
                String.valueOf(seed),
                String.valueOf(qLen),
                String.valueOf(dimensions),
                normalize ? "1" : "0",
                String.valueOf(kMeansClusters),
                String.valueOf(seed),
                String.valueOf(segmentMethod),
                String.valueOf(indexLeafSize)
        ) + ".ser";
    }

    private void createTree(List<Entry<CandidateSegment, Geometry>> items){
//        Insert all the segments into the R-tree
        Logger.getGlobal().info("[Multi ST-index] Building the R-tree");
        tree = RTree.star().dimensions(treeDimensions).leafSize(buildTree ? indexLeafSize: Integer.MAX_VALUE).create(items);

        //            Do ad-hoc segmentation if necessary
        if (segmentMethod.equals(SegmentMethods.ADHOC)) {
            Logger.getGlobal().info("[Multi ST-index] Post-index segmentation");
            AdhocSegment.postIndexSegment(tree);
        }
    }

    public void buildIndex() {
//        Get all the segments from the dataset
        Logger.getGlobal().info("[Multi ST-index] Segmenting the fourier trails");
        final List<Entry<CandidateSegment, Geometry>> items = DFTUtils.getSegmentedFourierTrail(kMeans);

//        Create the R-tree
        createTree(items);
    }

    @Override
    public void saveIndex() {
        String filename = getIndexPath();

//        Serialize the tree
        try{
            Logger.getGlobal().info("[Multi ST-index] Saving the index to file");
            FileOutputStream fileOut = new FileOutputStream(filename);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tree);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Algorithm loadIndex() {
        String filename = getIndexPath();

        try {
            Logger.getGlobal().info("[Multi ST-index] Loading the index from file");
            FileInputStream fileIn = new FileInputStream(filename);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            tree = (RTree<CandidateSegment, Geometry>) in.readObject();
            Logger.getGlobal().info("[Multi ST-index] Index loaded");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return this;
    }



    public ArrayList<MSTuple3> kNN(int k, double[][] query) {
//        Compute the first f DFT-coefficients of the query, and prepare it for the R-tree search.
        final Tuple2<double[], LandmarkPortfolio> queryDFTs = DFTUtils.getQueryDFTs(query, kMeans);
        final double[] fftQFlat = queryDFTs._1();
        final LandmarkPortfolio queryPortfolio = queryDFTs._2();
        final MinimumBoundingRectangle queryMBR = new MinimumBoundingRectangle(fftQFlat, fftQFlat, kMeansClusters == 0 ? null: new LandmarkMBR(queryPortfolio));


//        Derive an initial top k.
        final ArrayList<MSTuple3> initialTopK = firstPass(k, query, queryMBR);
        final double kThDistance = initialTopK.get(k - 1).distance;
        Logger.getGlobal().info("[Multi ST-index] k-th distance after initial topK: " + kThDistance);

//        Derive the exact topK
        final ArrayList<MSTuple3> finalTopK = secondPass(k, query, queryMBR, kThDistance);

        if (finalTopK.isEmpty()) {
            Logger.getGlobal().warning("No results found for query");
        }
        return finalTopK;
    }

    private ArrayList<MSTuple3> firstPass(int k, double[][] query, MinimumBoundingRectangle queryMBR) {
        //        Get the closest r-tree entries (i.e., segments in the time series) to the query.
        long start = System.currentTimeMillis();
        final List<CandidateSegment> candidateSegments = closest(queryMBR, k);
        indexSearchTime += System.currentTimeMillis() - start;

        // Collect as map from timeseries to list of segments
        final Map<Integer, List<CandidateSegment>> groupedCandidateSegments = CandidateSegment.groupByTimeSeriesIndex(candidateSegments);

        //       Derive a current top k.
        start = System.currentTimeMillis();
        ArrayList<MSTuple3> initialTopK = postFilter(query, groupedCandidateSegments, k, Double.POSITIVE_INFINITY);
        exhaustiveTime += System.currentTimeMillis() - start;

        return initialTopK;
    }

    private ArrayList<MSTuple3> secondPass(int k, double[][] query, MinimumBoundingRectangle queryMBR, double threshold) {
        //        Get the final candidates from the R-tree.
        long start = System.currentTimeMillis();
        final Iterable<Entry<CandidateSegment, Geometry>> finalCandidates = tree.search(queryMBR, new RunningThreshold(threshold));

        final List<CandidateSegment> finalCandidatesList = lib.getStream(finalCandidates).map(Entry::value).collect(toList());

//        Group the final candidates by time series
        final Map<Integer, List<CandidateSegment>> groupedFinalCandidates = CandidateSegment.groupByTimeSeriesIndex(finalCandidatesList);

        Logger.getGlobal().info("[Multi ST-index] Under threshold candidates: " + finalCandidatesList.size() + " coming from " + groupedFinalCandidates.size() + " time series");
        indexSearchTime += System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        final ArrayList<MSTuple3> finalTopK = postFilter(query, groupedFinalCandidates, k, threshold);
        exhaustiveTime += System.currentTimeMillis() - start;

        return finalTopK;
    }

    private List<CandidateSegment> closest(MinimumBoundingRectangle queryMBR, int k) {
        final RunningThreshold runningThreshold = new RunningThreshold(Double.POSITIVE_INFINITY);
        final BoundedPriorityQueue<CandidateSegment> q = new BoundedPriorityQueue<>(k, Comparator.comparingDouble(CandidateSegment::getCurrLB));

        for (Entry<CandidateSegment, Geometry> entry : tree.search(queryMBR, runningThreshold)) {
            final CandidateSegment candidateSegment = entry.value();

            if (!DataManager.supportsQuery(candidateSegment.getTimeSeriesIndex())) {
                continue;
            }

//            Compute the distance to the query
            q.add(candidateSegment);

//            Update the running threshold
            if (q.size() == k) {
                runningThreshold.set(q.peek().getCurrLB());
            }
        }
        return q.asOrderedList();
    }



    /**
     * Filter our false positives by computing the actual distances with the MASS algorithm.
     */
    public ArrayList<MSTuple3> postFilter(double[][] query, Map<Integer, List<CandidateSegment>> groupedCandidateSegments,
                                          int k, double initialKthDistance) {
        final double[] querySumOfSquares = DFTUtils.getSumsOfSquares(query);
        final double[][][] qNorms = DFTUtils.getQNorms(query);

        final PriorityBlockingQueue<MSTuple3> topK = new PriorityBlockingQueue<>(k, MSTuple3.compareByDistanceReversed());

        lib.getStream(groupedCandidateSegments.entrySet()).forEach(entry -> {
            final List<CandidateSegment> segments = entry.getValue();

            final double threshold = topK.size() == k ? topK.peek().distance() : initialKthDistance;
            final List<CandidateSegment> newSegments = DFTUtils.optimizeCandidates(segments, threshold);

//            Compute the actual distances with MASS
            DFTUtils.updateTopKWithMASS(newSegments, qNorms, querySumOfSquares, topK, k);
        });

        final ArrayList<MSTuple3> result = new ArrayList<>(topK);
        result.sort(MSTuple3.compareByDistance());
        return result;
    }

    private long recursiveTreeMemoryUsage(Node<CandidateSegment, Geometry> node) {
        if (node instanceof NonLeafDefault) {
            NonLeafDefault<CandidateSegment, Geometry> nonLeaf = (NonLeafDefault<CandidateSegment, Geometry>) node;
            long memoryUsage = tree.dimensions() * 8L * 2L + 4L * 2L; // mins, maxes, 2 pointers
            for (Node<CandidateSegment, Geometry> child : nonLeaf.children()) {
                memoryUsage += recursiveTreeMemoryUsage(child);
            }
            return memoryUsage;
        } else if (node instanceof LeafDefault) {
            LeafDefault<CandidateSegment, Geometry> leaf = (LeafDefault<CandidateSegment, Geometry>) node;
            long memoryUsage = tree.dimensions() * 8L * 2L; // mins, maxes
            memoryUsage += leaf.count() * tree.dimensions() * 8L * 2L + leaf.count() * (4L * 3L + (kMeansClusters == 0 ? 0 : dimensions * 2L * 8L + dimensions * 4L)); // min and max coefficients, (timeSeries, subSequence, subsequenceEnd, landmarkDistanceMin, landmarkDistanceMax, preferredLandmarks)
            return memoryUsage;
        }
        throw new IllegalStateException("Unknown node type " + node.getClass());
    }

    public double memoryUsage() {
        return recursiveTreeMemoryUsage(tree.root().get()) / 1000000d;
    }
}
