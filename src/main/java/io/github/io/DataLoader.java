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

        final int timeseriesForQueries = includeQueriesInIndex ? 0 : (int) Math.ceil(nQueries / 10.);
        final int NToQuery = N + timeseriesForQueries;
        double[][][] data = parser.parseData(NToQuery, maxM, qLen, channels);

        if (!includeQueriesInIndex) {
            // Generate all queries from nQueries / 10 time series
            if (data.length < NToQuery) {
                Logger.getGlobal().warning("Not enough time series to generate queries, so reducing the number of time series in the dataset used for indexing");
                N = data.length - timeseriesForQueries;
                if (N < 1) throw new IllegalArgumentException("Not enough time series");
            }

            withheldTimeSeries = new double[timeseriesForQueries][][];
            System.arraycopy(data, N, withheldTimeSeries, 0, timeseriesForQueries);
            final double[][][] newDataset = new double[N][][];
            System.arraycopy(data, 0, newDataset, 0, N);
            data = newDataset;
        }

        Parameters.datasetSize = 0L;
        nSubsequences = 0L;
        for (double[][] timeSeries : data) {
            nSubsequences += timeSeries[0].length - qLen + 1L;
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
