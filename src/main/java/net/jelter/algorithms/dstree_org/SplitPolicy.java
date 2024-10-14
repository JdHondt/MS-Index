package net.jelter.algorithms.dstree_org;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: wangyang
 * Date: 11-7-7
 * Time: 下午7:03
 * To change this template use File | Settings | File Templates.
 */
public class SplitPolicy implements Serializable {
    short splitFrom;    // segment start point
    short splitTo;      // segment end point
    @Setter
    @Getter
    INodeSegmentSplitPolicy nodeSegmentSplitPolicy;     // segment split policy

    int indicatorIdx;   // 0 for mean, 1 for stdev. Choose a metric to split certain segment
    double indicatorSplitValue;     //  The specific value for the metric to splie

    @Setter
    @Getter
    ISeriesSegmentSketcher seriesSegmentSketcher;   // describe a segment by some indicators(metrics)

    boolean routeToLeft(double[] series) {
        SeriesSegmentSketch seriesSegmentSketch = seriesSegmentSketcher.doSketch(series, splitFrom, splitTo);
        return (seriesSegmentSketch.indicators[indicatorIdx] < indicatorSplitValue);
    }

    @Override
    public String toString() {
        return "from:" + splitFrom + " to:" + splitTo + " value:" + indicatorSplitValue + " name:" + nodeSegmentSplitPolicy.getClass().getSimpleName();
    }
}
