package io.github.algorithms.dstree_org;

import io.github.utils.MSTuple3;
import io.github.utils.lib;
import lombok.Getter;
import io.github.algorithms.Algorithm;
import io.github.algorithms.dstree_org.util.TimeSeries;
import io.github.io.DataManager;
import org.apache.commons.math3.util.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.io.DataManager.data;
import static io.github.utils.Parameters.*;


public class MVDSTreeOrg extends Algorithm implements Serializable {
    @Getter
    public DSTreeOrg[] indices;

    public MVDSTreeOrg() {
        indices = new DSTreeOrg[channels];
        for (int i = 0; i < channels; i++) {
            indices[i] = new DSTreeOrg();
        }
    }

    @Override
    public void buildIndex() {
        final TimeSeries[][][] subsequences = new TimeSeries[N][channels][];
        for (int n = 0; n < N; n++) {
            for (int d = 0; d < channels; d++) {
                int nSubsequences = data[n][d].length - qLen + 1;
                subsequences[n][d] = new TimeSeries[nSubsequences];
                for (int i = 0; i < nSubsequences; i++) {
                    subsequences[n][d][i] = new TimeSeries(n, d, i);
                }
            }
        }

        lib.getStream(IntStream.range(0, channels).boxed()).forEach(i -> indices[i].buildIndex(subsequences, i));
    }

    @Override
    public void saveIndex(){
        String fileName = getIndexPath();

//        Serialize this object
        try {
            FileOutputStream fileOut = new FileOutputStream(fileName);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
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
            return (MVDSTreeOrg) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    private ArrayList<Pair<Double, double[]>> exhaustiveTopK(List<TimeSeries> candidates, double[][] query, int k) {
        final Comparator<Pair<Double, double[]>> comparator = (o1, o2) -> Double.compare(o2.getKey(), o1.getKey());
        final PriorityBlockingQueue<Pair<Double, double[]>> topK = new PriorityBlockingQueue<>(k, comparator);

        lib.getStream(candidates).forEach(
                ts -> {
                    final double[] dists = new double[query.length];
                    double totalDist = 0;
                    final int timeSeries = ts.tsId;
                    final int subSequence = ts.ssId;
                    for (int d : selectedVariatesIdx) {
                        final double dist = DataManager.dist(query[d], timeSeries, subSequence, d);
                        totalDist += dist;
                        dists[d] = dist;
                    }
                    final Pair<Double, double[]> pair = new Pair<>(totalDist, dists);
                    if (topK.size() < k) {
                        topK.add(pair);
                    } else {
                        if (totalDist < topK.peek().getKey()) {
                            topK.add(pair);
                            topK.poll();
                        }
                    }
                }
        );
        return new ArrayList<>(topK);
    }

    private double getDist(double[][][] cache, double[] query, int timeSeries, int subSequenceNr, int d) {
        if (cache[timeSeries][d][subSequenceNr] == 0) {
            cache[timeSeries][d][subSequenceNr] = DataManager.dist(query, timeSeries, subSequenceNr, d);
        }
        return cache[timeSeries][d][subSequenceNr];
    }

    //    General merging algorithm from the paper. Named BaselineQuery in the paper
    public List<MSTuple3> kNN(int k, double[][] query) {
        long start = System.currentTimeMillis();
        //        Get approximate kNN for each index in parallel
        final List<TimeSeries> candidates = lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .map(i -> indices[i].approxKNN(query[i], k))
                .flatMap(List::stream).distinct().collect(Collectors.toList());

//        Exhaustive full distance calculation, and get topK
        final List<Pair<Double, double[]>> approxTopK = exhaustiveTopK(candidates, query, k);

//        Get thresholds for each dimension
        final double[] thresholds = new double[query.length];
        for (int d : selectedVariatesIdx) {
            for (Pair<Double, double[]> pairs : approxTopK) {
                double[] dists = pairs.getValue();
                thresholds[d] = Math.max(thresholds[d], dists[d]);
            }
        }

        final double[][][] distanceCache = new double[N][channels][];
        final boolean[][] computed = new boolean[N][];
        for (int n = 0; n < N; n++) {
            final int M = data[n][0].length;
            computed[n] = new boolean[M - qLen + 1];

            final int subSequenceCount = M - qLen + 1;
            for (int d : selectedVariatesIdx) {
                distanceCache[n][d] = new double[subSequenceCount];
            }
        }
        long _setUpTime = System.currentTimeMillis();
        setUpTime += _setUpTime - start;

        final PriorityBlockingQueue<MSTuple3> topKTracking = new PriorityBlockingQueue<>(k, MSTuple3.compareByDistanceReversed());
//        Get final topK candidates
        final List<TimeSeries> entries = lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .flatMap(i -> {
                    final List<TimeSeries> underThreshold = indices[i].thresholdQuery(query[i], thresholds[i]);
                    return lib.getStream(underThreshold);
                }).collect(Collectors.toUnmodifiableList());

        segmentsUnderThreshold.getAndAdd(entries.size());
        long _indexSearchTime = System.currentTimeMillis();
        indexSearchTime += _indexSearchTime - setUpTime;

        lib.getStream(entries)
                .forEach(s -> {
                    final int timeSeries = s.tsId;
                    final int subSequenceNr = s.ssId;
                    final int dimension = s.dimension;

                    if (computed[timeSeries][subSequenceNr]) {
                        return;
                    }
                    computed[timeSeries][subSequenceNr] = true;
                    subsequencesExhChecked.getAndIncrement();

                    //                    Check for FP here
                    if (getDist(distanceCache, query[dimension], timeSeries, subSequenceNr, dimension) > thresholds[dimension]) {
                        return;
                    }

//                    Compute distance
                    double dist = 0;
                    for (int d : selectedVariatesIdx) {
                        dist += DataManager.dist(query[d], timeSeries, subSequenceNr, d);
                    }

                    if (topKTracking.size() < k) {
                        topKTracking.add(new MSTuple3(dist, timeSeries, subSequenceNr));
                    } else if (dist < topKTracking.peek().distance()) {
                        topKTracking.add(new MSTuple3(dist, timeSeries, subSequenceNr));
                        topKTracking.poll();
                    }
                });
        exhaustiveTime += System.currentTimeMillis() - indexSearchTime;
        return new ArrayList<>(topKTracking);
    }

    @Override
    public double memoryUsage() {
//        Get the size of the saved index by extracting the size of the serialized file
        double totalSize = 0;
        for (DSTreeOrg index : indices) {
            totalSize += index.memoryUsage();
        }
        return totalSize;
    }
}
