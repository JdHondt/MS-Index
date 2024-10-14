package io.github.algorithms.dstree_org.util;

import io.github.io.DataManager;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

import static io.github.io.DataManager.timeSeriesStarts;
import static io.github.utils.Parameters.normalize;
import static io.github.utils.Parameters.qLen;

public class TimeSeries implements Serializable {
    public final int tsId;
    public final int dimension;
    public final int ssId;
    public double distance;

    public TimeSeries(int tsId, int dimension, int ssId) {
        this.tsId = tsId;
        this.dimension = dimension;
        this.ssId = ssId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeSeries that = (TimeSeries) o;
        return tsId == that.tsId && ssId == that.ssId;
    }

    @Override
    public int hashCode() {
        return timeSeriesStarts[tsId] + ssId;
    }

    @Override
    public String toString() {
        return "TimeSeries(ts=" + tsId + ", ss=" + ssId + ", distance=" + distance + ")";
    }

    public int length() {
        return qLen;
    }

    public double[] getTs() {
        if (!normalize) {
            return Arrays.copyOfRange(DataManager.data[tsId][dimension], ssId, ssId + qLen);
        }
        double[] subsequence = new double[qLen];
        for (int i = 0; i < qLen; i++) {
            subsequence[i] = (DataManager.data[tsId][dimension][ssId + i] - DataManager.means[tsId][dimension][ssId]) / DataManager.stds[tsId][dimension][ssId];
        }
        return subsequence;
    }

    public double computeDistance(double[] query) {
        distance = DataManager.dist(query, tsId, ssId, dimension);
        return distance;
    }

    public static Comparator<TimeSeries> compareByDistanceReversed() {
        return (a, b) -> Double.compare(b.distance, a.distance);
    }
}
