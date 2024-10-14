package io.github.io;

import io.github.utils.lib;

import java.util.Collections;
import java.util.logging.Logger;

import static io.github.utils.Parameters.*;

public class DataLoader {

    //    Load data from a file based on the global parameters
    public static double[][][] loadData() {
        Logger.getGlobal().info("Loading data");

        DatasetParser parser = new DatasetParser(dataPath);
        channels = parser.scanDirectory();

//        Shuffle the paths
        if (seed != 0) Collections.shuffle(variatePaths, random);

        double[][][] data = parser.parseData(N, maxM, qLen, channels);

//        Normalize data if needed
        if (normalize) {
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    data[i][j] = lib.znorm(data[i][j]);
                }
            }
        }

        N = data.length;
        nSubsequences = parser.subsequences;

        return data;
    }

}
