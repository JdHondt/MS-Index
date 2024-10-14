package io.github.algorithms.dstree_org.util;


import io.github.algorithms.dstree_org.Node;
import io.github.utils.lib;

import java.io.IOException;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Created by IntelliJ IDEA.
 * User: zetasq
 * Date: 1/31/11
 * Time: 9:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class DistUtil {

    public static double minDist(String fileName, double[] queryTs) throws IOException {
        //open index file;
        TimeSeriesReader timeSeriesReader = new TimeSeriesReader(fileName);
        timeSeriesReader.open();
        double ShortestDist = Double.POSITIVE_INFINITY;
        double tempDist;
        while (timeSeriesReader.hasNext()) {
            double[] tempTimeseries = timeSeriesReader.next();
            tempDist = lib.euclideanSquaredDistance(queryTs, tempTimeseries);
            if (tempDist < ShortestDist) {
                ShortestDist = tempDist;
            }
        }
        timeSeriesReader.close();
        return ShortestDist;
    }


    public static TimeSeries[] minDistBinaryKnn(Node bsfNode, double[] queryTs, int k, int dimension) throws IOException {
        //open index file;
        List<TimeSeries> tss = bsfNode.getTimeSeriesList();
        return knn(tss, queryTs, k);
    }

    public static double[] minDist(String fileName, double[][] multiTs) throws IOException {
        //open index file;
        TimeSeriesReader timeSeriesReader = new TimeSeriesReader(fileName);
        timeSeriesReader.open();
        double[] shortestDists = lib.minArray(multiTs.length);
        double tempDist;
        while (timeSeriesReader.hasNext()) {
            double[] tempTimeseries = timeSeriesReader.next();
            for (int i = 0; i < shortestDists.length; i++) {
                tempDist = lib.euclideanSquaredDistance(multiTs[i], tempTimeseries);
                if (tempDist < shortestDists[i]) {
                    shortestDists[i] = tempDist;
                }
            }
        }
        timeSeriesReader.close();
        return shortestDists;
    }

    public static double[] currentQueryTs;


    public static TimeSeries[] knn(List<TimeSeries> tss, double[] queryTs, int k) throws IOException{
        assert k>0;
        currentQueryTs = queryTs;
        PriorityQueue<TimeSeries> heap = new PriorityQueue<>(k, TimeSeries.compareByDistanceReversed());
        for(TimeSeries ts:tss){
            if(heap.size()<k) {
                ts.computeDistance(queryTs);
                heap.add(ts);
            } else {
                TimeSeries maxDistTs = heap.poll();
                // maxDistTs's distance<ts
                double newDist = ts.computeDistance(queryTs);
                if(Double.compare(maxDistTs.distance, newDist) < 0) {
                    heap.add(maxDistTs);
                } else {
                  heap.add(ts);
                }
            }
        }
        int hs = heap.size();
        TimeSeries[] results = new TimeSeries[hs];
        int i=0;
        while (!heap.isEmpty()){
            results[hs - i - 1] = heap.remove();
            i+=1;
        }
        return results;
    }


    // Actual min distance
    public static double minDist(double[][] tss, double[] queryTs) throws IOException {
        double ShortestDist = Double.POSITIVE_INFINITY;
        double tempDist;
//        int resultLineNo = -1;
        for (double[] doubles : tss) {
            tempDist = lib.euclideanSquaredDistance(queryTs, doubles);
            if (tempDist < ShortestDist) {
                ShortestDist = tempDist;
//                resultLineNo = i;
            }
        }
//        System.out.println("resultLineNo = " + resultLineNo);
        return ShortestDist;
    }

    public static double minDist(List<double[]> tss, double[] queryTs) throws IOException {
        double ShortestDist = Double.POSITIVE_INFINITY;
        double tempDist;
//        int resultLineNo = -1;
        for (double[] doubles : tss) {
            tempDist = lib.euclideanSquaredDistance(queryTs, doubles);
            if (tempDist < ShortestDist) {
                ShortestDist = tempDist;
//                resultLineNo = i;
            }
        }
//        System.out.println("resultLineNo = " + resultLineNo);
        return ShortestDist;
    }

}
