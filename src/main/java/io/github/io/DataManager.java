package io.github.io;

import io.github.utils.Range;

import java.util.HashSet;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.*;
import static io.github.utils.Parameters.qLen;
import static io.github.utils.lib.pow2;
import static io.github.utils.lib.std;

public class DataManager {
    public static double[][][] data;
    public static double[][][] means;
    public static double[][][] stds;
    public static double[][][] squaredSums;
    public static double[][][] queries;
    public static int[] timeSeriesStarts;
    public static int[] supportedDimensions;
    private static int variatesUsedInQuery;
    public static HashSet<Integer> selectedQueriesSet;
    public static HashSet<Integer> selectedVariatesSet;

    public static void computeMeansStds() {
        timeSeriesStarts = new int[N];
        means = new double[N][channels][];
        stds = new double[N][channels][];
        IntStream.range(0, N).parallel().forEach(n -> {
            final int nrOfSubsequences = noSubsequences(n);
            if (n != 0) {
                timeSeriesStarts[n] = timeSeriesStarts[n - 1] + nrOfSubsequences;
            }

            for (int d = 0; d < channels; d++) {
                means[n][d] = new double[nrOfSubsequences];
                stds[n][d] = new double[nrOfSubsequences];

                for (int m = 0; m < nrOfSubsequences; m++) {
                    double LS = 0;
                    double SS = 0;
                    for (int i = 0; i < qLen; i++) {
                        LS += data[n][d][m + i];
                        SS += pow2(data[n][d][m + i]);
                    }
                    means[n][d][m] = LS / qLen;
                    stds[n][d][m] = std(SS, LS, qLen);
                }
            }
        });
    }

    public static void computeSquaredSums() {
        timeSeriesStarts = new int[N];
        squaredSums = new double[N][channels][];
        IntStream.range(0, N).parallel().forEach(n -> {
            final int nrOfSubsequences = noSubsequences(n);
            if (n != 0) {
                timeSeriesStarts[n] = timeSeriesStarts[n - 1] + nrOfSubsequences;
            }

            for (int d = 0; d < channels; d++) {
                squaredSums[n][d] = new double[nrOfSubsequences];
                for (int m = 0; m < nrOfSubsequences; m++) {
                    double sum = 0;
                    for (int i = 0; i < qLen; i++) {
                        sum += pow2(data[n][d][m + i]);
                    }
                    squaredSums[n][d][m] = sum;
                }
            }
        });
    }

    public static double getMean(int timeSeries, int dimension, int subSequence) {
        if (!normalize) return 0;
        return means[timeSeries][dimension][subSequence];
    }

    public static double getStd(int timeSeries, int dimension, int subSequence) {
        return stds[timeSeries][dimension][subSequence];
    }

    public static double getSquaredSum(int timeSeries, int dimension, int i) {
        if (normalize) return 0.;
        return squaredSums[timeSeries][dimension][i];
    }

    public static double[] getTimeSeries(int timeSeries, int dimension) {
        return data[timeSeries][dimension];
    }

    //    Get a univariate subsequence
    public static double[] getSubSequence(int timeSeries, int dimension, int subSequence) {
        final double[] subSeq = new double[qLen];

        if (!normalize) {
            System.arraycopy(data[timeSeries][dimension], subSequence, subSeq, 0, qLen);
            return subSeq;
        }

        final double std = getStd(timeSeries, dimension, subSequence);
        if (std == 0) {
            return subSeq;
        }

        final double mean = getMean(timeSeries, dimension, subSequence);
        for (int j = 0; j < qLen; j++) {
            subSeq[j] = (data[timeSeries][dimension][subSequence + j] - mean) / std;
        }
        return subSeq;
    }

    //    Get a multivariate subsequence
    public static double[][] getSubSequence(int timeSeries, int subSequence) {
        final double[][] subSeq = new double[channels][qLen];

        for (int i = 0; i < channels; i++) {
            subSeq[i] = getSubSequence(timeSeries, i, subSequence);
        }
        return subSeq;
    }

    public static int getM(int timeSeries) {
        for (double[] variate : data[timeSeries]) {
            if (variate != null) {
                return variate.length;
            }
        }
        return 0;
    }

    public static int noSubsequences(int timeSeries) {
        final int m = getM(timeSeries);
        if (m == 0) {
            return 0;
        }
        return m - qLen + 1;
    }

    public static double dist(double[] query, int timeSeries, int subSequence, int dimension) {
        double dist = 0;

        if (!normalize) {
            for (int i = 0; i < query.length; i++) {
                dist += pow2(data[timeSeries][dimension][subSequence + i] - query[i]);
            }
            return dist;
        } else {
            final double std = stds[timeSeries][dimension][subSequence];
            if (std == 0) {
                return query.length;
            } else {
                for (int i = 0; i < query.length; i++) {
                    dist += pow2((data[timeSeries][dimension][subSequence + i] - means[timeSeries][dimension][subSequence]) / stds[timeSeries][dimension][subSequence] - query[i]);
                }
            }
        }
        return dist;
    }

    public static double[] getPartialSeriesForMASS(int timeSeries, int dimension, Range range) {
        final int segmentLength = range.getPower2Length();
        final int tsLength = range.getEnd() - range.getStart() + qLen;
        final double[] partialSeries = new double[segmentLength];
        System.arraycopy(data[timeSeries][dimension], range.getStart(), partialSeries, 0, tsLength);
        return partialSeries;
    }
}
