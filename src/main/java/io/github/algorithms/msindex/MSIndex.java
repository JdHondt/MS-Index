package io.github.algorithms.msindex;

import io.github.algorithms.Algorithm;
import io.github.utils.KMeans;
import io.github.algorithms.msindex.segmentation.AdhocSegment;
import io.github.algorithms.msindex.segmentation.SegmentMethods;
import io.github.utils.*;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.Node;
import io.github.utils.rtreemulti.RTree;
import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.internal.LeafDefault;
import io.github.utils.rtreemulti.internal.NonLeafDefault;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static io.github.utils.Parameters.*;

public class MSIndex extends Algorithm {
    final KMeans[] kMeans = new KMeans[channels];
    RTree<CandidateSegment, Geometry> tree;
    private final int treeDimensions = fourierLength();

//    Boolean indicating if we want to use the index or not (in 2nd case we stop at root)
    private boolean buildTree = true;

    public MSIndex(boolean buildTree) {
        this.buildTree = buildTree;

        DFTUtils.computeKMeansClusters(kMeans);
    }

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
                String.valueOf(channels),
                normalize ? "1" : "0",
                String.valueOf(kMeansClusters),
                String.valueOf(seed),
                String.valueOf(segmentMethod),
                String.valueOf(indexLeafSize)
        ) + ".ser";
    }

    private void createTree(List<Entry<CandidateSegment, Geometry>> items){
//        Insert all the segments into the R-tree
        Logger.getGlobal().info("[MS-Index] Building the R-tree");
        tree = RTree.star().dimensions(treeDimensions).leafSize(buildTree ? indexLeafSize: Integer.MAX_VALUE).create(items);

        //            Do ad-hoc segmentation if necessary
        if (segmentMethod.equals(SegmentMethods.ADHOC)) {
            Logger.getGlobal().info("[MS-Index] Post-index segmentation");
            AdhocSegment.postIndexSegment(tree);
        }
    }

    public void buildIndex() {
//        Get all the segments from the dataset
        Logger.getGlobal().info("[MS-Index] Segmenting the fourier trails");
        final List<Entry<CandidateSegment, Geometry>> items = DFTUtils.getSegmentedFourierTrail(kMeans);

//        Create the R-tree
        createTree(items);
    }

    @Override
    public void saveIndex() {
        String filename = getIndexPath();

//        Serialize the tree
        try{
            Logger.getGlobal().info("[MS-Index] Saving the index to file");
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
            Logger.getGlobal().info("[MS-Index] Loading the index from file");
            FileInputStream fileIn = new FileInputStream(filename);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            tree = (RTree<CandidateSegment, Geometry>) in.readObject();
            Logger.getGlobal().info("[MS-Index] Index loaded");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return this;
    }



    public ArrayList<CandidateMVSubsequence> kNN(int k, double[][] query) {
//        Compute the first f DFT-coefficients of the query, and prepare it for the R-tree search.
        final Tuple2<double[], LandmarkPortfolio> queryDFTs = DFTUtils.getQueryDFTs(query, kMeans);
        final double[] fftQFlat = queryDFTs._1();
        final LandmarkPortfolio queryPortfolio = queryDFTs._2();
        final MinimumBoundingRectangle queryMBR = new MinimumBoundingRectangle(fftQFlat, fftQFlat, kMeansClusters == 0 ? null: new LandmarkMBR(queryPortfolio));

//        Derive an initial top k.
        final ArrayList<CandidateMVSubsequence> initialTopK = firstPass(k, query, queryMBR);
        final double kThDistance = initialTopK.get(k - 1).totalDistance;
        Logger.getGlobal().info("[MS-Index] k-th distance after initial topK: " + kThDistance);

//        Derive the exact topK
        final ArrayList<CandidateMVSubsequence> finalTopK = secondPass(k, query, queryMBR, kThDistance);

        if (finalTopK.isEmpty()) {
            Logger.getGlobal().warning("No results found for query");
        }
        return finalTopK;
    }

    private ArrayList<CandidateMVSubsequence> firstPass(int k, double[][] query, MinimumBoundingRectangle queryMBR) {
        //        Get the closest r-tree entries (i.e., segments in the time series) to the query.
        long start = System.currentTimeMillis();
        final List<CandidateSegment> candidateSegments = closest(queryMBR, k);
        indexSearchTime += System.currentTimeMillis() - start;

        //       Derive a current top k.
        start = System.currentTimeMillis();
        ArrayList<CandidateMVSubsequence> initialTopK = postFilter(query, candidateSegments, k, Double.POSITIVE_INFINITY);
        exhaustiveTime += System.currentTimeMillis() - start;

        return initialTopK;
    }

    private ArrayList<CandidateMVSubsequence> secondPass(int k, double[][] query, MinimumBoundingRectangle queryMBR, double threshold) {
        //        Get the final candidates from the R-tree.
        long start = System.currentTimeMillis();
        final Iterable<Entry<CandidateSegment, Geometry>> finalCandidates = tree.search(queryMBR, new RunningThreshold(threshold));

        final List<CandidateSegment> finalCandidatesList = lib.getStream(finalCandidates).map(Entry::value).collect(toList());

        segmentsUnderThreshold.getAndAdd(finalCandidatesList.size());

        Logger.getGlobal().info("[MS-Index] Under threshold candidates: " + finalCandidatesList.size());
        indexSearchTime += System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        final ArrayList<CandidateMVSubsequence> finalTopK = postFilter(query, finalCandidatesList, k, threshold);
        exhaustiveTime += System.currentTimeMillis() - start;

        return finalTopK;
    }

    private List<CandidateSegment> closest(MinimumBoundingRectangle queryMBR, int k) {
        final RunningThreshold runningThreshold = new RunningThreshold(Double.POSITIVE_INFINITY);
        final BoundedPriorityQueue<CandidateSegment> q = new BoundedPriorityQueue<>(k, Comparator.comparingDouble(CandidateSegment::getCurrLB));

        for (Entry<CandidateSegment, Geometry> entry : tree.search(queryMBR, runningThreshold)) {
            final CandidateSegment candidateSegment = entry.value();

//            Compute the distance to the query
            q.add(candidateSegment);

//            Update the running threshold
            if (q.size() == k) {
                runningThreshold.set(q.peek().getCurrLB());
            }
        }
        return q.asOrderedList();
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
            memoryUsage += leaf.count() * tree.dimensions() * 8L * 2L + leaf.count() * (4L * 3L + (kMeansClusters == 0 ? 0 : channels * 2L * 8L + channels * 4L)); // min and max coefficients, (timeSeries, subSequence, subsequenceEnd, landmarkDistanceMin, landmarkDistanceMax, preferredLandmarks)
            return memoryUsage;
        }
        throw new IllegalStateException("Unknown node type " + node.getClass());
    }

    public double memoryUsage() {
        return recursiveTreeMemoryUsage(tree.root().get()) / 1000000d;
    }
}
