package io.github.algorithms;

import io.github.io.DataManager;
import io.github.utils.CandidateMVSubsequence;
import io.github.utils.lib;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.selectedVariatesIdx;

public class BruteForce extends Algorithm {

    public List<CandidateMVSubsequence> kNN(int k, double[][] query) {
        double[][][] dataset = DataManager.data;

        final PriorityBlockingQueue<CandidateMVSubsequence> topK = new PriorityBlockingQueue<>(k, CandidateMVSubsequence.compareByTotalDistanceReversed());

        lib.getStream(IntStream.range(0, dataset.length).boxed()).map(n -> Map.entry(n, -1)).flatMap(a -> {
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
            final CandidateMVSubsequence tt = new CandidateMVSubsequence(entry.getKey(), entry.getValue(), dist);

//            Add to topk
            if (topK.size() < k) {
                topK.add(tt);
            } else if (tt.totalDistance() < topK.peek().totalDistance()) {
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
