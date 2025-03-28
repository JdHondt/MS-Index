package io.github.algorithms;

import lombok.RequiredArgsConstructor;
import io.github.io.DataManager;
import io.github.utils.CandidateMVSubsequence;
import io.github.utils.CandidateSegment;
import io.github.utils.DFTUtils;
import io.github.utils.lib;
import pl.edu.icm.jlargearrays.ConcurrencyUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import static io.github.utils.Parameters.*;

@RequiredArgsConstructor
public abstract class Algorithm {

    public void run() {
        Logger logger = Logger.getGlobal();

        Algorithm algo = this;

//        Separate flow for different runtime modes
        switch (runtimeMode){
            case CORRECTNESS: // Index + Query + Check
                logger.info("Building index");
                buildIndexWrapper();

//                Save the index
//                logger.info("Saving index to file");
//                saveIndex();
//
////                Load the index
//                Logger.getGlobal().info("Loading index");
//                algo = loadIndex();
//                if (algo == null) algo = this;

//                Run the query
                logger.info("Querying");
                algo.queryAll();
                break;
            case FULL: // Index + Save + Query
                logger.info("Building index");
                buildIndexWrapper();

//                Save the index
                logger.info("Saving index to file");
                saveIndex();

//                Run the query
                logger.info("Querying");
                algo.queryAll();
                break;
            case QUERY: // (Index) + (Save) + Query
                //  Check if we have a saved index
                String indexPath = algo.getIndexPath();
                File file = new File(indexPath);
                if (file.exists()) {
                    logger.info("Index found, loading index");
                    algo = loadIndex();
                    if (algo == null) algo = this;
                } else { //  If no index is found, build it
                    logger.info("No index found, building index");
                    buildIndexWrapper();

//                    Save the index
                    logger.info("Saving index to file");
                    saveIndex();
                }

//                Run the query
                logger.info("Querying");
                algo.queryAll();
                break;
            case INDEX: // Index + Save
                logger.info("Building index");
                buildIndexWrapper();

//                Save the index
                logger.info("Saving index to file");
                saveIndex();
                break;
            case INDEX_NO_STORE: // Index
                logger.info("Building index");
                buildIndexWrapper();
                break;
            case FULL_NO_STORE: // Index + Query
                logger.info("Building index");
                buildIndexWrapper();

                logger.info("Querying");
                algo.queryAll();
                break;
            case CHECK_INDEX: // Index + Save + Remove
                logger.info("Building index");
                buildIndexWrapper();

//                Save the index
                logger.info("Saving index to file");
                saveIndex();

//                Check the size of the index by the size of the file
                File f = new File(getIndexPath());
                indexSize = f.length() / 1_000_000d; // in MB

//                Remove the index
                f.delete();
                break;
            default:
                throw new RuntimeException("Unknown runtime mode");
        }
    }

    public void queryAll() {
        Logger logger = Logger.getGlobal();
        logger.info("Starting querying process");

//        Make sure we are not using parallelism
        indexing = false;
        if (!parallel) {
            ConcurrencyUtils.setNumberOfThreads(1);
        }

        //        Query all and print results
        double[][][] queries = DataManager.queries;
        queryTimePerQuery = new long[queries.length];

        for (int i = 0; i < queries.length; i++) {
            long lstart = System.currentTimeMillis();
            logger.info(String.format("Querying %d/%d", i, queries.length));
            final List<CandidateMVSubsequence> results = kNN(K, queries[i]);
            if (printResults) {
//                Sort results by distance
                results.sort(Comparator.comparingDouble(CandidateMVSubsequence::totalDistance));
                logger.info(String.format("Results for query %d/%d: ", i, queries.length) + results.toString());
            }
            if (runtimeMode == RuntimeMode.CORRECTNESS) {
                final List<CandidateMVSubsequence> bruteForceResult = new BruteForce().kNN(K, queries[i]);
                bruteForceResult.sort(Comparator.comparingDouble(CandidateMVSubsequence::totalDistance));
                for (int j = 0; j < K; j++) {
                    if (!bruteForceResult.get(j).equals(results.get(j))) {
                        logger.severe(bruteForceResult.get(j).toString() + " != " + results.get(j).toString());
                        throw new RuntimeException("Incorrect result for query " + i);
                    }
                }
            }
            long time = System.currentTimeMillis() - lstart;
            queryTimePerQuery[i] = time;
            queryTime += time;
            currQueryId++;
        }
    }

    public abstract List<CandidateMVSubsequence> kNN(int k, double[][] query);

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
                String.valueOf(fftCoveredDistance),
                String.valueOf(kMeansClusters),
                String.valueOf(seed)
        ) + ".ser";
    }

    public void buildIndexWrapper() {
        long start = System.currentTimeMillis();
        buildIndex();
        indexBuildTime = System.currentTimeMillis() - start;
        System.gc();
    }

    public void saveIndex() {
    }

    public Algorithm loadIndex() {
        return this;
    }

    public abstract void buildIndex();

    public double memoryUsage() {
//        Get the size of the saved index by extracting the size of the serialized file
        String fileName = getIndexPath();

//        Check if file exists
        File file = new File(fileName);
        if (!file.exists()) {
            Logger.getGlobal().info("Trying to get memory usage of non-existing index");
            return 0;
        }

//        Get the size of the file
        long size = file.length();
        return size / 1000000d;
    }

    /**
     * Filter our false positives by computing the actual distances with the MASS algorithm.
     */
    public static ArrayList<CandidateMVSubsequence> postFilter(double[][] query, List<CandidateSegment> candidateSegments,
                                                        int k, double initialKthDistance) {
        // Collect as map from timeseries to list of segments
        final Map<Integer, List<CandidateSegment>> groupedCandidateSegments = CandidateSegment.groupByTimeSeriesIndex(candidateSegments);

        final double[] querySumOfSquares = DFTUtils.getSumsOfSquares(query);
        final double[][][] qNorms = DFTUtils.getQNorms(query);

        final PriorityBlockingQueue<CandidateMVSubsequence> topK = new PriorityBlockingQueue<>(k, CandidateMVSubsequence.compareByTotalDistanceReversed());

        lib.getStream(groupedCandidateSegments.entrySet()).forEach(entry -> {
            final List<CandidateSegment> segments = entry.getValue();

            final double threshold = topK.size() == k ? topK.peek().totalDistance() : initialKthDistance;
            final List<CandidateSegment> newSegments = DFTUtils.optimizeCandidates(segments, threshold);

//            Compute the actual distances with MASS
            DFTUtils.updateTopKWithMASS(newSegments, qNorms, querySumOfSquares, topK, k);
        });

        final ArrayList<CandidateMVSubsequence> result = new ArrayList<>(topK);
        result.sort(CandidateMVSubsequence.compareByTotalDistance());
        return result;
    }
}
