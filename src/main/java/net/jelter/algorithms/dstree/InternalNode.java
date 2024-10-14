package net.jelter.algorithms.dstree;

import java.util.ArrayList;

public class InternalNode implements Node {
    Node left;
    Node right;
    final SplittingPolicy splittingPolicy;
    final int splitPolicyIndex;
    final double[][] endPoints;
    final double splitPoint;

    public InternalNode(double[][] endPoints, LeafNode left, LeafNode right, SplittingPolicy splittingPolicy, int splittingPolicyIndex, double splitPoint) {
        this.left = left;
        this.right = right;
        this.splittingPolicy = splittingPolicy;
        this.splitPolicyIndex = splittingPolicyIndex;
        this.splitPoint = splitPoint;
        this.endPoints = endPoints;
        assert (splittingPolicy == SplittingPolicy.HSplitMean || splittingPolicy == SplittingPolicy.HSplitStd);
    }

    private double std(double SS, double LS, int n) {
        double val = SS / n - Math.pow(LS / n, 2);
        if (val > -1e-12 && val < 1e-12) {
            return 0;
        }
        return Math.sqrt(Math.abs(val));
    }

    @Override
    public boolean isFull() {
        return false;
    }

    public InternalNode convertToInternalNode(DSSubSequence newSequence) {
        return this;
    }

    public boolean isLeft(DSSubSequence ss) {
        int start = 0;
        if (splitPolicyIndex > 0) {
            start = (int) endPoints[splitPolicyIndex - 1][0];
        }
        int end = (int) endPoints[splitPolicyIndex][0];

        final int count = (int) endPoints[splitPolicyIndex][0] - start;

        double LS = 0;
        double SS = 0;
        for (double value : ss.values(start, end)) {
            LS += value;
            SS += Math.pow(value, 2);
        }
        final double mean = LS / count;
        final double std = std(SS, LS, count);

        if (splittingPolicy == SplittingPolicy.HSplitMean) {
            return mean < splitPoint;
        }
        return std < splitPoint;
    }

    @Override
    public ArrayList<DSSubSequence> getBest(SubSequence query) {
        boolean isLeft = isLeft(query);
        if (isLeft) {
            return left.getBest(query);
        } else {
            return right.getBest(query);
        }
    }

    @Override
    public double minimumDistance(SubSequence query) {
        int start = 0;
        double minimumDist = 0;
        for (double[] endPoint : endPoints) {
            final int end = (int) endPoint[0];
            final int count = end - start;

            double LS = 0;
            double SS = 0;
            for (double value : query.values(start, end)) {
                LS += value;
                SS += Math.pow(value, 2);
            }
            final double mean = LS / count;
            final double std = std(SS, LS, count);

            double LBM = 0;
            if (mean > endPoint[1]) {
                LBM = Math.pow(mean - endPoint[1], 2);
            } else if (mean < endPoint[2]) {
                LBM = Math.pow(mean - endPoint[2], 2);
            }
            double LBS = 0;
            if (std > endPoint[3]) {
                LBS = Math.pow(std - endPoint[3], 2);
            } else if (std < endPoint[4]) {
                LBS = Math.pow(std - endPoint[4], 2);
            }
            minimumDist += count * (LBM + LBS);
            start = end;
        }
        return minimumDist;
    }

    @Override
    public void insert(DSSubSequence ss) {
        final boolean isLeft = isLeft(ss);
        if (isLeft) {
            if (left.isFull()) {
                left = left.convertToInternalNode(ss);
            } else {
                left.insert(ss);
            }
        } else {
            if (right.isFull()) {
                right = right.convertToInternalNode(ss);
            } else {
                right.insert(ss);
            }
        }

        // Recalculate endpoints
        int start = 0;

        for (double[] endPoint : endPoints) {
            double LS = 0;
            double SS = 0;
            final int count = (int) endPoint[0] - start;
            int end = (int) endPoint[0];
            for (double value : ss.values(start, end)) {
                LS += value;
                SS += Math.pow(value, 2);
            }
            final double mean = LS / count;
            final double std = std(SS, LS, count);
            endPoint[1] = Math.max(endPoint[1], mean);
            endPoint[2] = Math.min(endPoint[2], mean);
            endPoint[3] = Math.max(endPoint[3], std);
            endPoint[4] = Math.min(endPoint[4], std);
            start = end;
        }
    }

    public int storageUsed() {
        int size = 4;

        size += left.storageUsed();
        size += right.storageUsed();

        size += endPoints.length * 5 * 2;

        return size;
    }
}
