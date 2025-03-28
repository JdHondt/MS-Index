package io.github.algorithms.kvmatch;

import io.github.algorithms.Algorithm;
import io.github.io.DataManager;
import io.github.utils.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.io.DataManager.data;
import static io.github.utils.Parameters.*;

public class MultivariateKVMatch extends Algorithm implements Serializable {

    final KVMatchIndex[][] kvMatches;

    public MultivariateKVMatch() {
        kvMatches = new KVMatchIndex[N][channels]; // One index per time series and channel
    }

//    Just check the first MTS with MASS to get the approximate kNN
    public List<CandidateSegment> approxKNN(int k, double[][] query){
        return List.of(new CandidateSegment(0, 0, DataManager.noSubsequences(0) - 1, null));
    }

    public List<CandidateSegment> thresholdQuery(int timeseriesId, double[] thresholds, List<Double>[] queryData){
//            Query the dimensions in parallel
        return lib.getStream(IntStream.of(selectedVariatesIdx).boxed()).flatMap(d -> {
            final KVMatchIndex kvMatch = kvMatches[timeseriesId][d];
            if (kvMatch instanceof NormQueryEngine) {
                // We use a very high threshold here but cannot use Infinity due to some weirdness in the KVMatch library
                return ((NormQueryEngine) kvMatch).query(queryData[d], thresholds[d], 99999999, Double.POSITIVE_INFINITY).stream();
            } else {
                return ((QueryEngine) kvMatch).query(queryData[d], thresholds[d]).stream();
            }
        })
                .map(CandidateSubsequence::toMVSubsequence)
                .distinct()
                .map(CandidateMVSubsequence::toCandidateSegment)
                .collect(Collectors.toUnmodifiableList());
    }

    public double[] getThresholds(Collection<CandidateMVSubsequence> topK){
        final double[] thresholds = new double[channels];
        for (int d : selectedVariatesIdx) {
            for (CandidateMVSubsequence candidate : topK) {
                thresholds[d] = Math.max(thresholds[d], candidate.getDimDist(d));
            }
        }
        return thresholds;
    }

    @Override
    public List<CandidateMVSubsequence> kNN(int k, double[][] query) {
        //        Get approximate kNN
        long start = System.currentTimeMillis();
        final List<CandidateSegment> candidateSegments = approxKNN(k, query);
        indexSearchTime += System.currentTimeMillis() - start;

        //        Exhaustive full distance calculation with MASS, and get topK
        start = System.currentTimeMillis();
        final List<CandidateMVSubsequence> approxTopK = postFilter(query, candidateSegments, k, Double.POSITIVE_INFINITY);
        exhaustiveTime += System.currentTimeMillis() - start;

//        Get initial thresholds
        double[] thresholds = getThresholds(approxTopK);

//        Convert the query to a list
        List<Double>[] queryData = new List[channels];
        for (int d = 0; d < channels; d++) {
            queryData[d] = new ArrayList<>(query[d].length);
            for (double v : query[d]) {
                queryData[d].add(v);
            }
        }

//        Create running topK
        PriorityBlockingQueue<CandidateMVSubsequence> topK = new PriorityBlockingQueue<>(k, CandidateMVSubsequence.compareByTotalDistanceReversed());
        topK.addAll(approxTopK);

//        Compute necessary ingredients for mass
        final double[] querySumOfSquares = DFTUtils.getSumsOfSquares(query);
        final double[][][] qNorms = DFTUtils.getQNorms(query);

//        Now iterately update topK over all other timeseries, and continously update thresholds
        for (int i = 1; i < N; i++) {
            List<CandidateSegment> newCandidates = thresholdQuery(i, thresholds, queryData);

//            Compress candidates
            final double kthDistance = topK.peek().totalDistance;
            newCandidates = DFTUtils.optimizeCandidates(newCandidates, kthDistance);

//            Update topK by exhaustively computing full distance with MASS
            start = System.currentTimeMillis();
            DFTUtils.updateTopKWithMASS(newCandidates, qNorms, querySumOfSquares, topK, k);
            exhaustiveTime += System.currentTimeMillis() - start;

//            Update thresholds
            thresholds = getThresholds(topK);
        }

//       Return the final topK
        return new ArrayList<>(topK);
    }



//    @Override
//    public double memoryUsage() {
//        long total = 0;
//        for (int n = 0; n < N; n++) {
//            for (int d = 0; d < dimensions; d++) {
//                total += kvMatches[n][d].memoryUsage();
//            }
//        }
//        return total / 1000000d;
//    }

    @Override
    public void buildIndex() {
        for (int n = 0; n < N; n++) {
            for (int d = 0; d < channels; d++) {
                if (normalize) {
                    kvMatches[n][d] = new NormQueryEngine(data[n][d], n, d);
                } else {
                    kvMatches[n][d] = new QueryEngine(data[n][d], n, d);
                }
            }
        }
    }

    @Override
    public void saveIndex(){
        String fileName = getIndexPath();

        try {
            FileOutputStream fileOut = new FileOutputStream(fileName);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Algorithm loadIndex(){
        String fileName = getIndexPath();

        try {
            FileInputStream fileIn = new FileInputStream(fileName);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            return (MultivariateKVMatch) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }
}
