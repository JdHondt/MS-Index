package net.jelter.utils;

public class DistanceBound {
    private final double LB;
    private final double UB;

    public DistanceBound(double LB, double UB) {
        this.LB = LB;
        this.UB = UB;
    }

    public double LB() {
        return LB;
    }

    public double UB() {
        return UB;
    }
}
