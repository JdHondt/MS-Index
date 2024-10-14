package net.jelter.algorithms.dstree;

import net.jelter.io.DataManager;
import net.jelter.utils.TeunTuple2;

import java.util.Iterator;

import static net.jelter.utils.Parameters.normalize;

public class DSSubSequence {
    private final int timeSeries;
    private final int subSequence;
    private final int dimension;

    public DSSubSequence(int timeSeries, int subSequence, int dimension) {
        this.timeSeries = timeSeries;
        this.subSequence = subSequence;
        this.dimension = dimension;
    }

    public double distance(double[] query) {
        return DataManager.dist(query, timeSeries, subSequence, dimension);
    }

    public TeunTuple2 getIdentifier() {
        return new TeunTuple2(timeSeries, subSequence);
    }

    @Override
    public String toString() {
        return "TimeSeries " + timeSeries + " subSequence " + subSequence;
    }

    public Iterable<Double> values(int start, int end) {
        final int realStart = start + subSequence;
        final int realEnd = end + subSequence;
        final double[] values = DataManager.getTimeSeries(timeSeries, dimension);
        final double std = DataManager.getStd(timeSeries, dimension, subSequence);
        final double mean = DataManager.getMean(timeSeries, dimension, subSequence);
        return () -> new Iterator<>() {
            int current = 0;

            @Override
            public boolean hasNext() {
                return current + realStart < realEnd;
            }

            @Override
            public Double next() {
                final double value = values[realStart + current];
                if (!normalize) {
                    current++;
                    return value;
                }

                if (std == 0) {
                    current++;
                    return 0.0;
                }
                current++;
                return (value - mean) / std;
            }
        };
    }

}
