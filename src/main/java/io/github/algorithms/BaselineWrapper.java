package io.github.algorithms;

import io.github.utils.CandidateMVSubsequence;
import io.github.utils.CandidateSegment;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.utils.Parameters.*;

public abstract class BaselineWrapper extends Algorithm {

    public abstract void buildIndex();

    public abstract List<CandidateSegment> approxKNN(int k, double[][] query);

    public abstract List<CandidateSegment> thresholdQuery(double[] thresholds, double[][] query);

    public List<CandidateMVSubsequence> kNN(int k, double[][] query){

        //        Get approximate kNN for each index in parallel
        long start = System.currentTimeMillis();
        final List<CandidateSegment> candidateSegments = approxKNN(k, query);
        indexSearchTime += System.currentTimeMillis() - start;

//        Exhaustive full distance calculation with MASS, and get topK
        start = System.currentTimeMillis();
        final List<CandidateMVSubsequence> approxTopK = postFilter(query, candidateSegments, k, Double.POSITIVE_INFINITY);
        exhaustiveTime += System.currentTimeMillis() - start;

        //        Get thresholds for each dimension
        final double[] thresholds = new double[query.length];
        for (int d : selectedVariatesIdx) {
            for (CandidateMVSubsequence candidate : approxTopK) {
                thresholds[d] = Math.max(thresholds[d], candidate.getDimDist(d));
            }
        }

        //        Get final topK candidates
        start = System.currentTimeMillis();
        final List<CandidateSegment> candidates = thresholdQuery(thresholds, query).stream()
                .collect(Collectors.toUnmodifiableList());
        indexSearchTime += System.currentTimeMillis() - start;
        segmentsUnderThreshold.getAndAdd(candidates.size());

        //        Get final topK through an exhaustive search with MASS
        start = System.currentTimeMillis();
        final List<CandidateMVSubsequence> finalTopK = postFilter(query, candidates, k, Double.POSITIVE_INFINITY);
        exhaustiveTime += System.currentTimeMillis() - start;

        return finalTopK;
    }


}
