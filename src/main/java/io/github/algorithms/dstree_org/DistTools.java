package io.github.algorithms.dstree_org;

import io.github.algorithms.dstree_org.util.CalcUtil;

import java.io.IOException;

public class DistTools {

    /**
     * using avg and std
     *
     * @param node
     * @param queryTs
     * @return
     * @throws IOException
     */
    public static double minDist(Node node, double[] queryTs) {
        double sum = 0;
        short[] points = node.nodePoints;
        double[] avg = CalcUtil.avgBySegments(queryTs, points);
        double[] stdDev = CalcUtil.devBySegments(queryTs, points);

        for (int i = 0; i < avg.length; i++) {
            //use mean and standardDeviation to estimate the distance
            double tempDist = 0;
            //stdDev out the range of min std and max std
            if ((stdDev[i] - node.nodeSegmentSketches[i].indicators[2]) * (stdDev[i] - node.nodeSegmentSketches[i].indicators[3]) > 0) {
                tempDist += Math.pow(Math.min(Math.abs(stdDev[i] - node.nodeSegmentSketches[i].indicators[2]), Math.abs(stdDev[i] - node.nodeSegmentSketches[i].indicators[3])), 2);
            }

            //avg out the range of min mean and max mean
            if ((avg[i] - node.nodeSegmentSketches[i].indicators[0]) * (avg[i] - node.nodeSegmentSketches[i].indicators[1]) > 0) {
                tempDist += Math.pow(Math.min(Math.abs(avg[i] - node.nodeSegmentSketches[i].indicators[0]), Math.abs(avg[i] - node.nodeSegmentSketches[i].indicators[1])), 2);
            }
            sum += tempDist * node.getSegmentLength(i);
        }
//        sum = Math.sqrt(sum);
        return sum;
    }


}
