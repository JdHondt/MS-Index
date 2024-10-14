package net.jelter.utils;

import lombok.NonNull;

import static net.jelter.io.DataManager.timeSeriesStarts;

public class MSTuple2 {
    @NonNull
    public final int timeSeriesIndex;
    @NonNull
    public final int subSequenceIndex;

    public MSTuple2(@NonNull int timeSeriesIndex, @NonNull int subSequenceIndex) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.subSequenceIndex = subSequenceIndex;
    }

    @Override
    public boolean equals(Object obj) {
        final MSTuple2 other = (MSTuple2) obj;
        return this.subSequenceIndex == other.subSequenceIndex && this.timeSeriesIndex == other.timeSeriesIndex;
    }

    @Override
    public int hashCode() {
        return timeSeriesStarts[timeSeriesIndex] + subSequenceIndex;
    }
}
