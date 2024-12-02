package io.github.io;

import io.github.utils.Parameters;
import io.github.utils.lib;

import java.util.Collections;
import java.util.logging.Logger;

import static io.github.utils.Parameters.*;

public class DataLoader {
    public static double[][][] withheldTimeSeries;

    //    Load data from a file based on the global parameters
    public static double[][][] loadData() {
        Logger.getGlobal().info("Loading data");

        DatasetParser parser = new DatasetParser(dataPath);
        channels = parser.scanDirectory();

//        Shuffle the paths
        if (seed != 0) Collections.shuffle(variatePaths, random);

        final int timeseriesForQueries = queryFromIndexed ? 0 : Math.min(N, nQueries);
//        final int NToQuery = N + timeseriesForQueries;
        double[][][] data = parser.parseData(N, maxM, qLen, channels);

        if (qLen == -1) qLen = (int) Math.ceil(.2 * maxM);
        qLen = Math.max(qLen, Math.min(20, maxM)); // at least 20, if not possible, maxM

        if (!queryFromIndexed) {
            // Generate all queries from nQueries / 10 time series
//            if (data.length < N) {
//                Logger.getGlobal().warning("Not enough time series to generate queries, so reducing the number of time series in the dataset used for indexing");
//                N = data.length - timeseriesForQueries;
//                if (N < 1) throw new IllegalArgumentException("Not enough time series");
//            }

            withheldTimeSeries = new double[timeseriesForQueries][][];

//            Take the 2Q tails of the time series to be used for queries
            for (int i = 0; i < timeseriesForQueries; i++) {
//                Get a random time series
                final int idx = random.nextInt(data.length);
                final double[][] timeSeries = data[idx];
                final int nVariates = timeSeries.length;
                final int m = timeSeries[0].length;
                final int tailLength = Math.min(m, qLen * 2);

//                Create the tail
                final double[][] withheldTs = new double[nVariates][tailLength];
                for (int j = 0; j < nVariates; j++) {
                    System.arraycopy(timeSeries[j], m - tailLength, withheldTs[j], 0, tailLength);
                }

                withheldTimeSeries[i] = withheldTs;

//                Remove the tail from the time series
                if (m - tailLength > qLen) {
                    final double[][] newTimeSeries = new double[nVariates][m - tailLength];
                    for (int j = 0; j < nVariates; j++) {
                        System.arraycopy(timeSeries[j], 0, newTimeSeries[j], 0, m - tailLength);
                    }
                    data[idx] = newTimeSeries;
                }
            }
        }

        Parameters.datasetSize = 0L;
        nSubsequences = 0L;
        for (double[][] timeSeries : data) {
            final long add = timeSeries[0].length - qLen + 1L;
            if (add < 1) {
                throw new IllegalArgumentException("qLen > time series length");
            }

            nSubsequences += add;
            for (double[] variates : timeSeries) {
                Parameters.datasetSize += variates.length;
            }
        }

//        Normalize data if needed
        if (normalize) {
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    data[i][j] = lib.znorm(data[i][j]);
                }
            }
        }

        N = data.length;

        return data;
    }

}
