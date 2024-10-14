package net.jelter.algorithms.dstree;

import java.util.ArrayList;
import java.util.Map;

public class LeafNode implements Node {
    int remainingSpace;
    final double[][] endPoints;
    SplittingPolicy bestSplittingPolicy;
    int bestSplittingPolicyIndex;
    double[] bestSplittingLeft;
    double[] bestSplittingRight;
    double splitPoint;
    static final SplittingPolicy[] splittingPolicies = {SplittingPolicy.HSplitMean, SplittingPolicy.HSplitStd, SplittingPolicy.VSplitL, SplittingPolicy.VSplitR};
    final boolean isRoot;


    public LeafNode(double[][] endPoints, int capacity, boolean isRoot) {
        this.isRoot = isRoot;
        this.endPoints = endPoints;
        this.remainingSpace = capacity;
        bulkSequences.ensureCapacity(capacity + 1);
    }

    public ArrayList<DSSubSequence> getSubSequences() {
        return bulkSequences;
    }

    private double std(double SS, double LS, int n) {
        double val = SS / n - Math.pow(LS / n, 2);
        if (val > -1e-12 && val < 1e-12) {
            return 0;
        }
        return Math.sqrt(Math.abs(val));
    }


    private void computeBestSplit(DSSubSequence[] subSequences) {
        double bestBenefit = Double.POSITIVE_INFINITY;

        final double[][] means = new double[subSequences.length][endPoints.length];
        final double[][] std = new double[subSequences.length][endPoints.length];

        if (!isRoot) {
            for (int i = 0; i < subSequences.length; i++) {
                int start = 0;
                for (int j = 0; j < endPoints.length; j++) {
                    int end = (int) endPoints[j][0];
                    double LS = 0;
                    double SS = 0;
                    final int count = end - start;
                    for (double value : subSequences[i].values(start, end)) {
                        LS += value;
                        SS += Math.pow(value, 2);
                    }
                    means[i][j] = LS / count;
                    std[i][j] = std(SS, LS, count);
                    start = end;
                }
            }
        }

        double totalQOSRemainder = 0;
        int start = 0;
        for (double[] endPoint : endPoints) {
            int end = (int) endPoint[0];
            totalQOSRemainder += (end - start) * (Math.pow(endPoint[1] - endPoint[2], 2) + Math.pow(endPoint[3], 2));
            start = end;
        }

        for (SplittingPolicy split : splittingPolicies) {
            for (int i = 0; i < endPoints.length; i++) {
                double[] endPoint = endPoints[i]; // (range endpoint, mean max, mean min, std max, std min)

                int rangeStart = 0;
                if (i > 0) {
                    rangeStart = (int) endPoints[i - 1][0];
                }
                final int rangeEnd = (int) endPoint[0];
                int rangeCount = rangeEnd - rangeStart;
                if (rangeCount == 1) continue;

                final double QOSRemainder = totalQOSRemainder - (rangeEnd - rangeStart) * (Math.pow(endPoint[1] - endPoint[2], 2) + Math.pow(endPoint[3], 2));

                switch (split) {
                    case HSplitMean: {
                        if (isRoot) continue;
                        final double middle = (endPoint[1] + endPoint[2]) / 2;
                        double maxMeanLeft = Double.NEGATIVE_INFINITY;
                        double minMeanLeft = Double.POSITIVE_INFINITY;
                        double maxStdLeft = 0;

                        double maxMeanRight = Double.NEGATIVE_INFINITY;
                        double minMeanRight = Double.POSITIVE_INFINITY;
                        double maxStdRight = 0;

                        for (int j = 0; j < subSequences.length; j++) {
                            if (means[j][i] < middle) {
                                maxMeanLeft = Math.max(maxMeanLeft, means[j][i]);
                                minMeanLeft = Math.min(minMeanLeft, means[j][i]);
                                maxStdLeft = Math.max(maxStdLeft, std[j][i]);
                            } else {
                                maxMeanRight = Math.max(maxMeanRight, means[j][i]);
                                minMeanRight = Math.min(minMeanRight, means[j][i]);
                                maxStdRight = Math.max(maxStdRight, std[j][i]);
                            }
                        }
                        final double leftQOS = rangeCount * (Math.pow(maxMeanLeft - minMeanLeft, 2) + Math.pow(maxStdLeft, 2));
                        final double rightQOS = rangeCount * (Math.pow(maxMeanRight - minMeanRight, 2) + Math.pow(maxStdRight, 2));

                        final double benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = split;
                            bestSplittingPolicyIndex = i;
                            splitPoint = middle;
                        }
                        break;
                    }
                    case HSplitStd: {
                        if (isRoot) continue;
                        final double middle = (endPoint[3] + endPoint[4]) / 2;
                        double maxMeanLeft = Double.NEGATIVE_INFINITY;
                        double minMeanLeft = Double.POSITIVE_INFINITY;
                        double maxStdLeft = 0;
                        double maxMeanRight = Double.NEGATIVE_INFINITY;
                        double minMeanRight = Double.POSITIVE_INFINITY;
                        double maxStdRight = 0;


                        for (int j = 0; j < subSequences.length; j++) {
                            if (std[j][i] < middle) {
                                maxMeanLeft = Math.max(maxMeanLeft, means[j][i]);
                                minMeanLeft = Math.min(minMeanLeft, means[j][i]);
                                maxStdLeft = Math.max(maxStdLeft, std[j][i]);
                            } else {
                                maxMeanRight = Math.max(maxMeanRight, means[j][i]);
                                minMeanRight = Math.min(minMeanRight, means[j][i]);
                                maxStdRight = Math.max(maxStdRight, std[j][i]);
                            }
                        }
                        final double leftQOS = rangeCount * (Math.pow(maxMeanLeft - minMeanLeft, 2) + Math.pow(maxStdLeft, 2));
                        final double rightQOS = rangeCount * (Math.pow(maxMeanRight - minMeanRight, 2) + Math.pow(maxStdRight, 2));
                        final double benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = split;
                            bestSplittingPolicyIndex = i;
                            splitPoint = middle;
                        }
                        break;
                    }
                    case VSplitL: {
                        final int middleSplit = (rangeStart + rangeEnd) / 2;
                        final int leftRangeCount = middleSplit - rangeStart;
                        final int rightRangeCount = rangeEnd - middleSplit;

                        if (leftRangeCount == 0 || rightRangeCount == 0) continue;

                        double maxMeanLeft = Double.NEGATIVE_INFINITY;
                        double minMeanLeft = Double.POSITIVE_INFINITY;
                        double minStdLeft = Double.POSITIVE_INFINITY;
                        double maxStdLeft = 0;
                        double maxMeanRight = Double.NEGATIVE_INFINITY;
                        double minMeanRight = Double.POSITIVE_INFINITY;
                        double maxStdRight = 0;
                        double minStdRight = Double.POSITIVE_INFINITY;

                        final double[] meansLeft = new double[subSequences.length];
                        final double[] stdsLeft = new double[subSequences.length];

                        for (int j = 0; j < subSequences.length; j++) {
                            double leftLS = 0;
                            double leftSS = 0;
                            for (double value : subSequences[j].values(rangeStart, middleSplit)) {
                                leftLS += value;
                                leftSS += Math.pow(value, 2);
                            }
                            meansLeft[j] = leftLS / leftRangeCount;
                            stdsLeft[j] = std(leftSS, leftLS, leftRangeCount);

                            maxMeanLeft = Math.max(maxMeanLeft, meansLeft[j]);
                            minMeanLeft = Math.min(minMeanLeft, meansLeft[j]);
                            minStdLeft = Math.min(minStdLeft, stdsLeft[j]);
                            maxStdLeft = Math.max(maxStdLeft, stdsLeft[j]);

                            double rightLS = 0;
                            double rightSS = 0;
                            for (double value : subSequences[j].values(middleSplit, rangeEnd)) {
                                rightLS += value;
                                rightSS += Math.pow(value, 2);
                            }
                            double meanRight = rightLS / rightRangeCount;
                            maxMeanRight = Math.max(maxMeanRight, meanRight);
                            minMeanRight = Math.min(minMeanRight, meanRight);
                            double stdRight = std(rightSS, rightLS, rightRangeCount);
                            maxStdRight = Math.max(maxStdRight, stdRight);
                            minStdRight = Math.min(minStdRight, stdRight);
                        }

                        final double leftMeanSplit = (maxMeanLeft + minMeanLeft) / 2;
                        final double leftStdSplit = (maxStdLeft + minStdLeft) / 2;

                        // Mean-based split
                        double maxMeanLeftLeftM = Double.NEGATIVE_INFINITY;
                        double minMeanLeftLeftM = Double.POSITIVE_INFINITY;
                        double maxStdLeftLeftM = 0;
                        double maxMeanLeftRightM = Double.NEGATIVE_INFINITY;
                        double minMeanLeftRightM = Double.POSITIVE_INFINITY;
                        double maxStdLeftRightM = 0;

                        // Std-based split
                        double maxMeanLeftLeftV = Double.NEGATIVE_INFINITY;
                        double minMeanLeftLeftV = Double.POSITIVE_INFINITY;
                        double maxStdLeftLeftV = 0;
                        double maxMeanLeftRightV = Double.NEGATIVE_INFINITY;
                        double minMeanLeftRightV = Double.POSITIVE_INFINITY;
                        double maxStdLeftRightV = 0;

                        for (int j = 0; j < subSequences.length; j++) {
                            if (meansLeft[j] < leftMeanSplit) {
                                maxMeanLeftLeftM = Math.max(maxMeanLeftLeftM, meansLeft[j]);
                                minMeanLeftLeftM = Math.min(minMeanLeftLeftM, meansLeft[j]);
                                maxStdLeftLeftM = Math.max(maxStdLeftLeftM, stdsLeft[j]);
                            } else {
                                maxMeanLeftRightM = Math.max(maxMeanLeftRightM, meansLeft[j]);
                                minMeanLeftRightM = Math.min(minMeanLeftRightM, meansLeft[j]);
                                maxStdLeftRightM = Math.max(maxStdLeftRightM, stdsLeft[j]);
                            }

                            if (stdsLeft[j] < leftStdSplit) {
                                maxMeanLeftLeftV = Math.max(maxMeanLeftLeftV, meansLeft[j]);
                                minMeanLeftLeftV = Math.min(minMeanLeftLeftV, meansLeft[j]);
                                maxStdLeftLeftV = Math.max(maxStdLeftLeftV, stdsLeft[j]);
                            } else {
                                maxMeanLeftRightV = Math.max(maxMeanLeftRightV, meansLeft[j]);
                                minMeanLeftRightV = Math.min(minMeanLeftRightV, meansLeft[j]);
                                maxStdLeftRightV = Math.max(maxStdLeftRightV, stdsLeft[j]);
                            }
                        }
                        double leftQOS = leftRangeCount * (Math.pow(maxMeanLeftLeftM - minMeanLeftLeftM, 2) + Math.pow(maxStdLeftLeftM, 2));
                        leftQOS += leftRangeCount * (Math.pow(maxMeanLeftRightM - minMeanLeftRightM, 2) + Math.pow(maxStdLeftRightM, 2));
                        leftQOS /= 2;
                        final double rightQOS = rightRangeCount * (Math.pow(maxMeanRight - minMeanRight, 2) + Math.pow(maxStdRight, 2));

                        double benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = SplittingPolicy.VSplitLMean;
                            bestSplittingPolicyIndex = i;
                            bestSplittingLeft = new double[]{middleSplit, maxMeanLeft, minMeanLeft, maxStdLeft, minStdLeft};
                            bestSplittingRight = new double[]{rangeEnd, maxMeanRight, minMeanRight, maxStdRight, minStdRight};
                            splitPoint = leftMeanSplit;
                        }

                        leftQOS = leftRangeCount * (Math.pow(maxMeanLeftLeftV - minMeanLeftLeftV, 2) + Math.pow(maxStdLeftLeftV, 2));
                        leftQOS += leftRangeCount * (Math.pow(maxMeanLeftRightV - minMeanLeftRightV, 2) + Math.pow(maxStdLeftRightV, 2));
                        leftQOS /= 2;
                        benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = SplittingPolicy.VSplitLStd;
                            bestSplittingPolicyIndex = i;
                            bestSplittingLeft = new double[]{middleSplit, maxMeanLeft, minMeanLeft, maxStdLeft, minStdLeft};
                            bestSplittingRight = new double[]{rangeEnd, maxMeanRight, minMeanRight, maxStdRight, minStdRight};
                            splitPoint = leftStdSplit;
                        }
                        break;
                    }
                    case VSplitR: {
                        int middleSplit = (rangeStart + rangeEnd) / 2;
                        final int leftRangeCount = middleSplit - rangeStart;
                        final int rightRangeCount = rangeEnd - middleSplit;

                        if (leftRangeCount == 0 || rightRangeCount == 0) continue;

                        double maxMeanLeft = Double.NEGATIVE_INFINITY;
                        double minMeanLeft = Double.POSITIVE_INFINITY;
                        double maxStdLeft = 0;
                        double minStdLeft = Double.POSITIVE_INFINITY;
                        double maxMeanRight = Double.NEGATIVE_INFINITY;
                        double minMeanRight = Double.POSITIVE_INFINITY;
                        double minStdRight = Double.POSITIVE_INFINITY;
                        double maxStdRight = 0;

                        double[] meansRight = new double[subSequences.length];
                        double[] stdsRight = new double[subSequences.length];

                        for (int j = 0; j < subSequences.length; j++) {
                            double leftLS = 0;
                            double leftSS = 0;
                            for (double value : subSequences[j].values(rangeStart, middleSplit)) {
                                leftLS += value;
                                leftSS += Math.pow(value, 2);
                            }
                            double meanLeft = leftLS / leftRangeCount;
                            maxMeanLeft = Math.max(maxMeanLeft, meanLeft);
                            minMeanLeft = Math.min(minMeanLeft, meanLeft);
                            double varianceLeft = std(leftSS, leftLS, leftRangeCount);
                            maxStdLeft = Math.max(maxStdLeft, varianceLeft);
                            minStdLeft = Math.min(minStdLeft, varianceLeft);

                            double rightLS = 0;
                            double rightSS = 0;
                            for (double value : subSequences[j].values(middleSplit, rangeEnd)) {
                                rightLS += value;
                                rightSS += Math.pow(value, 2);
                            }
                            meansRight[j] = rightLS / rightRangeCount;
                            stdsRight[j] = std(rightSS, rightLS, rightRangeCount);

                            maxMeanRight = Math.max(maxMeanRight, meansRight[j]);
                            minMeanRight = Math.min(minMeanRight, meansRight[j]);
                            minStdRight = Math.min(minStdRight, stdsRight[j]);
                            maxStdRight = Math.max(maxStdRight, stdsRight[j]);
                        }

                        double rightMeanSplit = (maxMeanRight + minMeanRight) / 2;
                        double rightStdSplit = (maxStdRight + minStdRight) / 2;

                        // Mean-based split
                        double maxMeanRightLeftM = Double.NEGATIVE_INFINITY;
                        double minMeanRightLeftM = Double.POSITIVE_INFINITY;
                        double maxStdRightLeftM = 0;
                        double maxMeanRightRightM = Double.NEGATIVE_INFINITY;
                        double minMeanRightRightM = Double.POSITIVE_INFINITY;
                        double maxStdRightRightM = 0;

                        // Std-based split
                        double maxMeanRightLeftV = Double.NEGATIVE_INFINITY;
                        double minMeanRightLeftV = Double.POSITIVE_INFINITY;
                        double maxStdRightLeftV = 0;
                        double maxMeanRightRightV = Double.NEGATIVE_INFINITY;
                        double minMeanRightRightV = Double.POSITIVE_INFINITY;
                        double maxStdRightRightV = 0;

                        for (int j = 0; j < subSequences.length; j++) {
                            if (meansRight[j] < rightMeanSplit) {
                                maxMeanRightLeftM = Math.max(maxMeanRightLeftM, meansRight[j]);
                                minMeanRightLeftM = Math.min(minMeanRightLeftM, meansRight[j]);
                                maxStdRightLeftM = Math.max(maxStdRightLeftM, stdsRight[j]);
                            } else {
                                maxMeanRightRightM = Math.max(maxMeanRightRightM, meansRight[j]);
                                minMeanRightRightM = Math.min(minMeanRightRightM, meansRight[j]);
                                maxStdRightRightM = Math.max(maxStdRightRightM, stdsRight[j]);
                            }

                            if (stdsRight[j] < rightStdSplit) {
                                maxMeanRightLeftV = Math.max(maxMeanRightLeftV, meansRight[j]);
                                minMeanRightLeftV = Math.min(minMeanRightLeftV, meansRight[j]);
                                maxStdRightLeftV = Math.max(maxStdRightLeftV, stdsRight[j]);
                            } else {
                                maxMeanRightRightV = Math.max(maxMeanRightRightV, meansRight[j]);
                                minMeanRightRightV = Math.min(minMeanRightRightV, meansRight[j]);
                                maxStdRightRightV = Math.max(maxStdRightRightV, stdsRight[j]);
                            }
                        }
                        double rightQOS = rightRangeCount * (Math.pow(maxMeanRightLeftM - minMeanRightLeftM, 2) + Math.pow(maxStdRightLeftM, 2));
                        rightQOS += rightRangeCount * (Math.pow(maxMeanRightRightM - minMeanRightRightM, 2) + Math.pow(maxStdRightRightM, 2));
                        rightQOS /= 2;
                        final double leftQOS = leftRangeCount * (Math.pow(maxMeanLeft - minMeanLeft, 2) + Math.pow(maxStdLeft, 2));

                        double benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = SplittingPolicy.VSplitRMean;
                            bestSplittingPolicyIndex = i;
                            bestSplittingLeft = new double[]{middleSplit, maxMeanLeft, minMeanLeft, maxStdLeft, minStdLeft};
                            bestSplittingRight = new double[]{rangeEnd, maxMeanRight, minMeanRight, maxStdRight, minStdRight};
                            splitPoint = rightMeanSplit;
                        }

                        rightQOS = rightRangeCount * (Math.pow(maxMeanRightLeftV - minMeanRightLeftV, 2) + Math.pow(maxStdRightLeftV, 2));
                        rightQOS += rightRangeCount * (Math.pow(maxMeanRightRightV - minMeanRightRightV, 2) + Math.pow(maxStdRightRightV, 2));
                        rightQOS /= 2;

                        benefit = QOSRemainder + (leftQOS + rightQOS) / 2;

                        if (!Double.isNaN(benefit) && bestBenefit > benefit) {
                            bestBenefit = benefit;
                            bestSplittingPolicy = SplittingPolicy.VSplitRStd;
                            bestSplittingPolicyIndex = i;
                            bestSplittingLeft = new double[]{middleSplit, maxMeanLeft, minMeanLeft, maxStdLeft, minStdLeft};
                            bestSplittingRight = new double[]{rangeEnd, maxMeanRight, minMeanRight, maxStdRight, minStdRight};
                            splitPoint = rightStdSplit;
                        }
                        break;
                    }
                }
            }
        }

        if (Double.isInfinite(bestBenefit)) {
            bestSplittingPolicy = null;
        }
    }

    @Override
    public boolean isFull() {
        return remainingSpace == 0;
    }

    public Node convertToInternalNode(DSSubSequence newSequence) {
        final int capacity = bulkSequences.size();
        bulkSequences.add(newSequence);
        final DSSubSequence[] subSequencesArray = new DSSubSequence[bulkSequences.size()];
        bulkSequences.toArray(subSequencesArray);
        computeBestSplit(subSequencesArray);

        if (bestSplittingPolicy == null) {
            remainingSpace = capacity;
            return this;
        }

        final LeafNode left;
        final LeafNode right;
        final double[][] endPointsNew, endPointsLeft, endPointsRight;

        if (bestSplittingPolicy == SplittingPolicy.HSplitMean || bestSplittingPolicy == SplittingPolicy.HSplitStd) {
            endPointsNew = endPoints;
            endPointsLeft = new double[endPoints.length][5];
            endPointsRight = new double[endPoints.length][5];
            for (int i = 0; i < endPointsNew.length; i++) {
                double[] endPoint = endPointsNew[i];

                endPoint[1] = Double.NEGATIVE_INFINITY;
                endPoint[2] = Double.POSITIVE_INFINITY;
                endPoint[3] = Double.NEGATIVE_INFINITY;
                endPoint[4] = Double.POSITIVE_INFINITY;

                endPointsLeft[i] = endPoint.clone();
                endPointsRight[i] = endPoint.clone();
            }
            left = new LeafNode(endPointsLeft, capacity, false);
            right = new LeafNode(endPointsRight, capacity, false);
        } else {
            endPointsNew = new double[endPoints.length + 1][5];
            endPointsLeft = new double[endPoints.length + 1][5];
            endPointsRight = new double[endPoints.length + 1][5];
            boolean addOne = false;
            for (int i = 0; i < endPoints.length; i++) {
                if (i == bestSplittingPolicyIndex) {
                    endPointsNew[i] = bestSplittingLeft;
                    endPointsNew[i + 1] = bestSplittingRight;
                    addOne = true;
                } else {
                    endPointsNew[i + (addOne ? 1 : 0)] = endPoints[i];
                }
            }
            for (int i = 0; i < endPointsNew.length; i++) {
                double[] endPoint = endPointsNew[i];

                endPoint[1] = Double.NEGATIVE_INFINITY;
                endPoint[2] = Double.POSITIVE_INFINITY;
                endPoint[3] = Double.NEGATIVE_INFINITY;
                endPoint[4] = Double.POSITIVE_INFINITY;

                endPointsLeft[i] = endPoint.clone();
                endPointsRight[i] = endPoint.clone();
            }

            left = new LeafNode(endPointsLeft, capacity, false);
            right = new LeafNode(endPointsRight, capacity, false);
            switch (bestSplittingPolicy) {
                case VSplitLMean: {
                    bestSplittingPolicy = SplittingPolicy.HSplitMean;
                    break;
                }
                case VSplitLStd: {
                    bestSplittingPolicy = SplittingPolicy.HSplitStd;
                    break;
                }
                case VSplitRMean: {
                    bestSplittingPolicy = SplittingPolicy.HSplitMean;
                    bestSplittingPolicyIndex++;
                    break;
                }
                case VSplitRStd: {
                    bestSplittingPolicy = SplittingPolicy.HSplitStd;
                    bestSplittingPolicyIndex++;
                    break;
                }
            }
        }
        final InternalNode internalNode = new InternalNode(endPointsNew, left, right, bestSplittingPolicy, bestSplittingPolicyIndex, splitPoint);
        for (DSSubSequence ss : bulkSequences) {
            internalNode.insert(ss);
        }
        bulkSequences.clear();
        return internalNode;
    }

    @Override
    public ArrayList<DSSubSequence> getBest(SubSequence query) {
        return bulkSequences;
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

    final private ArrayList<DSSubSequence> bulkSequences = new ArrayList<>();

    @Override
    public void insert(DSSubSequence ss) {
        bulkSequences.add(ss);
        remainingSpace--;

        // Recalculate endpoints
        int start = 0;

        for (double[] endPoint : endPoints) {
            double LS = 0;
            double SS = 0;
            final int count = (int) endPoint[0] - start;
            final int end = (int) endPoint[0];
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
            start = (int) endPoint[0];
        }
    }

    public int storageUsed() {
        int size = 0;

        size += bulkSequences.size() * 3;
        size += endPoints.length * 5 * 2;

        return size;
    }
}
