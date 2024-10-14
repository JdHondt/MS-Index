package net.jelter.utils;

import com.google.common.util.concurrent.AtomicDouble;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.logging.Logger;

public class RunningThreshold {
    public AtomicDouble threshold;

    public RunningThreshold(double threshold) {
        this.threshold = new AtomicDouble(threshold);
    }

    public String toString() {
        return String.format("%.6f", threshold.get());
    }

    public double get() {
        return threshold.get();
    }

    synchronized public void set(double newThreshold) {
        if (newThreshold < threshold.get()) {
            threshold.set(newThreshold);
        }
    }

    public void reset() {
        threshold = new AtomicDouble(Double.POSITIVE_INFINITY);
    }

}