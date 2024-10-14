package net.jelter.algorithms.dstree;

import net.jelter.io.DataManager;
import net.jelter.algorithms.Algorithm;
import net.jelter.utils.TeunTuple2;
import net.jelter.utils.TeunTuple3;
import net.jelter.utils.lib;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.jelter.utils.Parameters.*;

public class MVDSTree extends Algorithm implements Serializable {
    private final DSTree[] trees;

    public MVDSTree() {
        this.trees = new DSTree[dimensions];

        for (int i = 0; i < dimensions; i++) {
            trees[i] = new DSTree(indexLeafSize, qLen);
        }
    }

    public void insert(ArrayList<DSSubSequence>[] subSequences) {
        IntStream.range(0, subSequences.length).parallel().forEach(d -> {
            Collections.shuffle(subSequences[d]); // We need randomness to avoid weird trees
            subSequences[d].forEach(trees[d]::insert);
        });
    }

    public Algorithm loadIndex() {
        return null;
    }

    public void buildIndex() {
        final ArrayList<DSSubSequence>[] allDSSubsequences = new ArrayList[dimensions];

        for (int d = 0; d < dimensions; d++) {
            allDSSubsequences[d] = new ArrayList<>();
        }

//        Create all subsequences
        final double[][][] dataset = DataManager.data;
        for (int n = 0; n < N; n++) {
            final int M = dataset[n][0].length;
            final int nrOfSubsequences = M - qLen + 1;

            for (int m = 0; m < nrOfSubsequences; m++) {
                for (int d = 0; d < dimensions; d++) {
                    allDSSubsequences[d].add(new DSSubSequence(n, m, d));
                }
            }
        }
        this.insert(allDSSubsequences);
    }

    public List<TeunTuple3> kNN(int k, double[][] query) {
        final SubSequence[] subSequence = new SubSequence[query.length];
        for (int d : selectedVariatesIdx) {
            subSequence[d] = new SubSequence(query[d]);
        }

        final List<double[]> topK = lib.getStream(IntStream.of(selectedVariatesIdx).boxed()).map(d -> trees[d].getBest(subSequence[d])
        ).flatMap(Collection::stream).map(DSSubSequence::getIdentifier).distinct().map(
                match -> {
                    final int timeSeries = match.timeSeriesIndex;
                    final int subSequenceNr = match.subSequenceIndex;

                    double[] dists = new double[query.length + 1];
                    for (int d : selectedVariatesIdx) {
                        final double dist = DataManager.dist(query[d], timeSeries, subSequenceNr, d);
                        dists[query.length] += dist;
                        dists[d] = dist;
                    }
                    return dists;
                }
        ).sorted(Comparator.comparingDouble(e -> e[query.length])).limit(k).collect(Collectors.toList());

        final double[] thresholds = new double[query.length];
        for (int d : selectedVariatesIdx) {
            for (double[] doubles : topK) {
                thresholds[d] = Math.max(thresholds[d], doubles[d]);
            }
        }

        return IntStream.range(0, trees.length).parallel().mapToObj(d -> {
            final ArrayList<TeunTuple2> strings = new ArrayList<>();
            trees[d].underThreshold(thresholds[d], subSequence[d], strings, query[d]);
            return strings.parallelStream();
        }).flatMap(entryStream -> entryStream).distinct().map(s -> {
            final int timeSeries = s.timeSeriesIndex;
            final int subSequenceNr = s.subSequenceIndex;
            double dist = 0;
            for (int d : selectedVariatesIdx) {
                dist += DataManager.dist(query[d], timeSeries, subSequenceNr, d);
            }
            return new TeunTuple3(dist, timeSeries, subSequenceNr);
        }).sorted(Comparator.comparingDouble(TeunTuple3::distance)).limit(k).collect(Collectors.toList());
    }
}
