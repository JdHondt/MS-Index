package net.jelter.io;

import net.jelter.utils.lib;

import static net.jelter.io.DataManager.selectedQueriesSet;

import java.util.Random;

public class WorkloadGenerator {

    /**
     * Generates a workload of queries based on a dataset.
     * It randomly selects a time series and subsequence from the dataset and adds noise to it.
     *
     * @param dataset:       The dataset to generate the workload from (shape; [nTimeSeries][nDimensions][nValues])
     * @param nQueries:      The number of queries to generate
     * @param qLen:          The length of the queries
     * @param queryNoiseEps: The amount of noise to add to the queries
     * @param normalize:     Whether to normalize the queries
     * @return An array of queries
     */
    public static double[][][] generateWorkload(double[][][] dataset, int nQueries, int qLen, double queryNoiseEps, boolean normalize, Random random) {
        final int nTimeSeries = dataset.length;
        final int nDimensions = dataset[0].length;

        if (random == null) {
            random = new Random();
        }

        double[][][] queries = new double[nQueries][nDimensions][qLen];
        selectedQueriesSet = new java.util.HashSet<>();

        for (int i = 0; i < nQueries; i++) {
            while (true) {
                final int randomTimeSeries = random.nextInt(nTimeSeries);
                double[][] subseq = dataset[randomTimeSeries];
                final int nValues = subseq[0].length;
                final int randomStart = random.nextInt(nValues - qLen + 1);

                // Check if this subsequence has variance over all dimensions
                boolean hasVariance = true;
                for (int d = 0; d < nDimensions; d++) {
                    final double std = DataManager.getStd(randomTimeSeries, d, randomStart);
                    if (std == 0) {
                        hasVariance = false;
                        break;
                    }
                }
                if (!hasVariance) {
                    continue;
                }
                selectedQueriesSet.add(randomTimeSeries);

                for (int j = 0; j < nDimensions; j++) {
                    double[] Q = new double[qLen];
                    double[] base = DataManager.getSubSequence(randomTimeSeries, j, randomStart);
                    for (int k = 0; k < qLen; k++) {
                        Q[k] = base[k] + (random.nextGaussian() * queryNoiseEps);
                    }
                    if (normalize) {
                        Q = lib.znorm(Q);
                    }
                    queries[i][j] = Q;
                }
                break;
            }
        }

        return queries;
    }
}
