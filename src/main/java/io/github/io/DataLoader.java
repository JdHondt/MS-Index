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

        double[][][] data = parser.parseData(N, maxM, qLen, channels);

        if (qLen == -1) qLen = (int) Math.ceil(.2 * maxM);
        qLen = Math.max(qLen, Math.min(20, maxM)); // at least 20, if not possible, maxM

        if (!queryFromIndexed) {
            withheldTimeSeries = new double[timeseriesForQueries][][];

//            Take the 2Q tails of the time series to be used for queries
            for (int i = 0; i < timeseriesForQueries; i++) {
                int idx;
                double[][] timeSeries;
                int m;

//                Get a random time series that is at least 4Q long
                if (maxM < qLen * 4) {
                    throw new IllegalArgumentException("Time series are too short for hold-out queries");
                }
                while (true){
                    idx = random.nextInt(data.length);
                    timeSeries = data[idx];
                    m = timeSeries[0].length;
                    if (m >= qLen * 4) break;
                }

                final int nVariates = timeSeries.length;
                final int tailLength = Math.min(m, qLen * 2);

//                Create the tail
                final double[][] withheldTs = new double[nVariates][tailLength];
                for (int j = 0; j < nVariates; j++) {
                    System.arraycopy(timeSeries[j], m - tailLength, withheldTs[j], 0, tailLength);
                }

                withheldTimeSeries[i] = withheldTs;

//                Remove the tail from the time series
                final double[][] newTimeSeries = new double[nVariates][m - tailLength];
                for (int j = 0; j < nVariates; j++) {
                    System.arraycopy(timeSeries[j], 0, newTimeSeries[j], 0, m - tailLength);
                }
                data[idx] = newTimeSeries;
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
