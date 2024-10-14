package net.jelter.algorithms.dstree;

import java.util.Iterator;

public class SubSequence extends DSSubSequence {

    final double[] values;

    public SubSequence(double[] values) {
        super(-1, -1, -1);
        this.values = values;
    }

    public Iterable<Double> values(int start, int end) {
        return () -> new Iterator<>() {
            int current = 0;

            @Override
            public boolean hasNext() {
                return current + start < end;
            }

            @Override
            public Double next() {
                double value = values[start + current];
                current++;
                return value;
            }
        };
    }
}

