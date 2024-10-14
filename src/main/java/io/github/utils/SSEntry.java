package io.github.utils;

public class SSEntry {
    public int timeSeriesIndex;
    public int subSequenceIndex;
    public int dimension;

    public SSEntry(int timeSeriesIndex, int subSequenceIndex, int dimension) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.subSequenceIndex = subSequenceIndex;
        this.dimension = dimension;
    }
}
