package net.jelter.io;

import net.jelter.utils.lib;

import java.util.Collections;
import java.util.logging.Logger;

import static net.jelter.utils.Parameters.*;

public class DataLoader {

    //    Load data from a file based on the global parameters
    public static double[][][] loadData() {
        Logger.getGlobal().info("Loading data");

        DatasetParser parser = new DatasetParser(dataPath);
        dimensions = parser.scanDirectory();

//        Shuffle the paths
        if (seed != 0) Collections.shuffle(variatePaths, random);

        double[][][] data = parser.parseData(N, maxM, qLen, dimensions);

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
