package io.github.io;

import io.github.utils.lib;

import static io.github.io.DataManager.selectedQueriesSet;

import java.util.Arrays;
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
    public static double[][][] generateWorkload(double[][][] dataset, int nQueries, int qLen,
                                                double queryNoiseEps, boolean fromIndex,
                                                boolean normalize, Random random) {
        final int nTimeSeries = dataset.length;
        final int nDimensions = dataset[0].length;

        if (random == null) {
            random = new Random();
        }

        double[][][] queries = new double[nQueries][nDimensions][qLen];
        selectedQueriesSet = new java.util.HashSet<>();

        for (int i = 0; i < nQueries; i++) {
            while (true) {
                final int randomTimeSeriesIdx = random.nextInt(nTimeSeries);
                double[][] randomTimeSeries = dataset[randomTimeSeriesIdx];
                final int nValues = randomTimeSeries[0].length;
                final int randomStart = random.nextInt(nValues - qLen + 1);

                // Check if this subsequence has variance over all dimensions
                boolean hasVariance = true;
                for (int d = 0; d < nDimensions; d++) {
                    double std;
                    if (fromIndex){
                        std = DataManager.getStd(randomTimeSeriesIdx, d, randomStart);
                    } else {
                        double LS = 0;
                        double SS = 0;
                        for (int k = 0; k < qLen; k++) {
                            double val = randomTimeSeries[d][randomStart + k];
                            LS += val;
                            SS += val * val;
                        }
                        std = lib.std(SS, LS, qLen);
                    }

                    if (std == 0) {
                        hasVariance = false;
                        break;
                    }
                }
                if (!hasVariance) {
                    continue;
                }
                selectedQueriesSet.add(randomTimeSeriesIdx);

                for (int j = 0; j < nDimensions; j++) {
                    double[] Q = fromIndex ? DataManager.getSubSequence(randomTimeSeriesIdx, j, randomStart):
                            Arrays.copyOfRange(randomTimeSeries[j], randomStart, randomStart + qLen);

//                    Add noise to query
                    if (queryNoiseEps > 0) {
                        for (int k = 0; k < qLen; k++) {
                            Q[k] += (random.nextGaussian() * queryNoiseEps);
                        }
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
