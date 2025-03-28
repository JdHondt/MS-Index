package io.github.utils;

import io.github.io.DataManager;

import java.io.Serializable;
import java.util.Comparator;

import static io.github.io.DataManager.timeSeriesStarts;
import static io.github.utils.Parameters.qLen;

public class CandidateSubsequence implements Serializable {
    public final int timeSeriesIndex;
    public final int subsequenceIndex;
    public final int dimension;
    public double distance;

    public CandidateSubsequence(int timeSeriesIndex, int dimension, int subsequenceIndex) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.dimension = dimension;
        this.subsequenceIndex = subsequenceIndex;
    }

    public double distance() {
        return distance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateSubsequence that = (CandidateSubsequence) o;
        return timeSeriesIndex == that.timeSeriesIndex && subsequenceIndex == that.subsequenceIndex;
    }

    @Override
    public int hashCode() {
        return timeSeriesStarts[timeSeriesIndex] + subsequenceIndex;
    }

    @Override
    public String toString() {
        return String.format("(T_{%d, %d}^{%d}, %.3f)", timeSeriesIndex, subsequenceIndex, dimension, distance);
    }

    public int length() {
        return qLen;
    }

    public double[] getData() {
        return DataManager.getSubSequence(timeSeriesIndex, dimension, subsequenceIndex);
    }

    public double computeDistance(double[] query) {
        distance = DataManager.dist(query, timeSeriesIndex, subsequenceIndex, dimension);
        return distance;
    }

    public static Comparator<CandidateSubsequence> compareByDistanceReversed() {
        return (a, b) -> Double.compare(b.distance, a.distance);
    }

    public CandidateMVSubsequence toMVSubsequence() {
        CandidateMVSubsequence candidate = new CandidateMVSubsequence(timeSeriesIndex, subsequenceIndex);
        candidate.setDimDist(dimension, distance);
        return candidate;

    }
}
