package io.github.algorithms.dstree_org.util;

import io.github.utils.CandidateSubsequence;
import io.github.utils.lib;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class TimeSeriesFileUtil {

    public static CandidateSubsequence[] readSeriesFromBinaryFileAtOnceFloat(String fileName, int dimension) throws IOException {
        long fileSize = new File(fileName).length();
        int count = (int) (fileSize / 4); // 4 bytes per integer
        FileInputStream fis = new FileInputStream(fileName);
        BufferedInputStream bis = new BufferedInputStream(fis);
        DataInputStream dis = new DataInputStream(bis);
        CandidateSubsequence[] tss = new CandidateSubsequence[count / 2]; // 2 integers per TimeSeries

        for (int i = 0; i < tss.length; i++) {
            int tsId = dis.readInt();
            int ssId = dis.readInt();
            tss[i] = new CandidateSubsequence(tsId, dimension, ssId);
        }

        dis.close();
        bis.close();
        fis.close();
        return tss;
    }


    public static double minDist(double[][] data, double[] queryTs) {
        double minDist = Double.POSITIVE_INFINITY;
        for (double[] datum : data) {
            double tempDist = lib.euclideanSquaredDistance(datum, queryTs);
            if (tempDist < minDist) minDist = tempDist;
        }
        return minDist;
    }


    public static NumberFormat formatter = new DecimalFormat("#0.0000");
}
