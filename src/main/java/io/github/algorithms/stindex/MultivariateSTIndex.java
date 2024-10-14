package io.github.algorithms.stindex;

import io.github.io.DataManager;
import io.github.algorithms.Algorithm;
import com.github.davidmoten.rtreemulti.geometry.Point;
import io.github.utils.*;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.*;

public class MultivariateSTIndex extends Algorithm {
    STIndex[] faloutsos;
    private FourierTrail[] fourierTrails;

    private ArrayList<Pair<Double, double[]>> exhaustiveTopK(List<MSTuple2> candidates, double[][] query, int k) {
        final Comparator<Pair<Double, double[]>> comparator = (o1, o2) -> Double.compare(o2.getKey(), o1.getKey());
        final PriorityBlockingQueue<Pair<Double, double[]>> topK = new PriorityBlockingQueue<>(k, comparator);

        lib.getStream(candidates).forEach(
                ts -> {
                    final double[] dists = new double[query.length];
                    double totalDist = 0;
                    final int timeSeries = ts.timeSeriesIndex;
                    final int subSequence = ts.subSequenceIndex;
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

    // Because we perform exact distance calculations for computing the variate-level thresholds, we cache the results
    // to avoid recomputing when computing the final distance over all variates
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
        List<MSTuple2> candidates = lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .map(i -> faloutsos[i].closest(Point.create(query[i]), k))
                .flatMap(List::stream).flatMap(e -> {
                    final ArrayList<MSTuple2> tuples = new ArrayList<>(e._1.getEnd() - e._1.getStart() + 1);
                    for (int i = e._1.getStart(); i <= e._1.getEnd(); i++) {
                        tuples.add(new MSTuple2(e._1.getTimeSeriesIndex(), i));
                    }
                    return tuples.stream();
                }).distinct().collect(Collectors.toList());

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
            final int noSubsequences = DataManager.noSubsequences(n);
            computed[n] = new boolean[noSubsequences];

            for (int d : selectedVariatesIdx) {
                distanceCache[n][d] = new double[noSubsequences];
            }
        }

        long _setUpTime = System.currentTimeMillis();
        setUpTime += _setUpTime - start;

        final PriorityBlockingQueue<MSTuple3> topKTracking = new PriorityBlockingQueue<>(k, MSTuple3.compareByDistanceReversed());

//        Get final topK candidates
//        This list does not contain time series that do not have all of the query's dimensions
        final List<SSEntry> entries = lib.getStream(IntStream.of(selectedVariatesIdx).boxed()).flatMap(d -> {
            final ArrayList<SSEntry> belowThreshold = new ArrayList<>();
            faloutsos[d].getBelowThreshold(query[d], thresholds[d], belowThreshold, d);
            return belowThreshold.parallelStream();
        }).collect(Collectors.toUnmodifiableList());

        segmentsUnderThreshold.getAndAdd(entries.size());
        long _indexSearchTime = System.currentTimeMillis();
        indexSearchTime += _indexSearchTime - _setUpTime;

        // We do this in two parts because otherwise java doesn't actually parallelize the stream
        lib.getStream(entries)
                .forEach(s -> {
                    final int timeSeries = s.timeSeriesIndex;
                    final int subSequenceNr = s.subSequenceIndex;
                    final int dimension = s.dimension;

                    if (computed[timeSeries][subSequenceNr]) {
                        // For performance reasons, we do not use a distinct call in the stream above, so we might get duplicates
                        return;
                    }
                    computed[timeSeries][subSequenceNr] = true;
                    subsequencesExhChecked.getAndIncrement();

//                    Check for FP here
                    if (getDist(distanceCache, query[dimension], timeSeries, subSequenceNr, dimension) > thresholds[dimension]) {
                        return;
                    }

//                    Compute the distance
                    double dist = 0;
                    for (int d : selectedVariatesIdx) {
                        dist += getDist(distanceCache, query[d], timeSeries, subSequenceNr, d);
                    }

                    if (topKTracking.size() < k) {
                        topKTracking.add(new MSTuple3(dist, timeSeries, subSequenceNr));
                    } else if (dist < topKTracking.peek().distance()) {
                        topKTracking.add(new MSTuple3(dist, timeSeries, subSequenceNr));
                        topKTracking.poll();
                    }
                });
        exhaustiveTime += System.currentTimeMillis() - _indexSearchTime;
        return new ArrayList<>(topKTracking);
    }

    public void buildIndex() {
//        First get all the fourierTrails, then build the indexes
        fourierTrails = new FourierTrail[N];
        for (int i = 0; i < N; i++) {
            fourierTrails[i] = DFTUtils.getFourierTrail(i, null);
        }

        faloutsos = new STIndex[channels];
        for (int d = 0; d < channels; d++) {
            faloutsos[d] = new STIndex(d, fourierTrails);
        }
        System.out.println("Index built");
        System.out.println("Number of nodes: " + countNodes());
    }

    @Override
    public double memoryUsage() {
        long total = 0;
        for (int d = 0; d < channels; d++) {
            total += faloutsos[d].memoryUsage();
        }
        return total / 1000000d;
    }

    private long countNodes() {
        long total = 0;
        for (int d = 0; d < channels; d++) {
            total += faloutsos[d].countNodes();
        }
        return total;
    }
}
