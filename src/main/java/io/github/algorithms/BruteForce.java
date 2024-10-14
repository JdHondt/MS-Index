package io.github.algorithms;

import io.github.io.DataManager;
import io.github.utils.MSTuple3;
import io.github.utils.lib;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static io.github.utils.Parameters.percentageVariatesUsed;
import static io.github.utils.Parameters.selectedVariatesIdx;

public class BruteForce extends Algorithm {

    public List<MSTuple3> kNN(int k, double[][] query) {
        double[][][] dataset = DataManager.data;

        final int variatesUsedInQuery = Arrays.stream(selectedVariatesIdx).map(d -> 1 << d).reduce(0, (a, b) -> a | b);
        final PriorityBlockingQueue<MSTuple3> topK = new PriorityBlockingQueue<>(k, MSTuple3.compareByDistanceReversed());

        lib.getStream(IntStream.range(0, dataset.length).boxed()).map(n -> Map.entry(n, -1)).flatMap(a -> {
            if (percentageVariatesUsed != 1) {
                // If one variate is included in the query but not in the time series, continue
                if ((DataManager.supportedDimensions[a.getKey()] & variatesUsedInQuery) != variatesUsedInQuery) {
                    return StreamSupport.stream(Spliterators.emptySpliterator(), false);
                }
            }

            final Map.Entry<Integer, Integer>[] entries = new Map.Entry[DataManager.noSubsequences(a.getKey())];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = Map.entry(a.getKey(), i);
            }
            return lib.getStream(entries);
        }).forEach(entry -> {
            double dist = 0;
            for (int d : selectedVariatesIdx) {
                dist += DataManager.dist(query[d], entry.getKey(), entry.getValue(), d);
            }
            final MSTuple3 tt = new MSTuple3(dist, entry.getKey(), entry.getValue());

//            Add to topk
            if (topK.size() < k) {
                topK.add(tt);
            } else if (tt.distance() < topK.peek().distance()) {
                topK.add(tt);
                topK.poll();
            }
        });
        return new ArrayList<>(topK);
    }

    public void buildIndex() {
        // No index to build
    }

    public double memoryUsage() {
        return 0;
    }

}
