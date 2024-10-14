package net.jelter.utils;


import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

@RequiredArgsConstructor
public class MSTuple3 {
    @NonNull
    public final double distance;
    @NonNull
    public final int timeSeriesIndex;
    @NonNull
    public final int subSequenceIndex;

    public double distance() {
        return distance;
    }

    public String toString() {
        return String.format("(T_{%d, %d}, %.3f)", timeSeriesIndex, subSequenceIndex, distance);
    }

    public static Comparator<MSTuple3> compareByDistance() {
        return Comparator.comparingDouble(a -> a.distance);
    }

    public static Comparator<MSTuple3> compareByDistanceReversed() {
        return (a, b) -> Double.compare(b.distance, a.distance);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MSTuple3)) {
            return false;
        }
        MSTuple3 other = (MSTuple3) o;
        // Ignore if the distance is the same as a top k with the same distances is allowed
        return Math.abs(other.distance - distance) < 0.001 || (other.timeSeriesIndex == timeSeriesIndex && other.subSequenceIndex == subSequenceIndex);
    }
}
