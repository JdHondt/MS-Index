package net.jelter.algorithms.dstree_org.util;

import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 * Created by IntelliJ IDEA.
 * User: wangyang
 * Date: 10-12-30
 * Time: 下午5:41
 * To change this template use File | Settings | File Templates.
 */
public class CalcUtil {
    public static double avg(double[] timeSeries) {
        return avg(timeSeries, 0, timeSeries.length);
    }

    public static Mean mean = new Mean();

    public static double avg(double[] timeSeries, int start, int end) {
        return mean.evaluate(timeSeries, start, (end - start));
    }

    public static Variance variance = new Variance(false);

    public static double deviation(double[] timeSeries, int start, int end) {
        return Math.sqrt(variance.evaluate(timeSeries, start, (end - start)));
    }

    public static double[] avgBySegments(double[] timeSeries, short[] segments) {
        double[] ret = new double[segments.length];
        int start = 0, end;
        for (int i = 0; i < ret.length; i++) {
            end = segments[i];
            ret[i] = avg(timeSeries, start, end);
            start = end;
        }
        return ret;
    }

    public static double[] devBySegments(double[] timeSeries, short[] segments) {
        double[] ret = new double[segments.length];
        int start = 0, end;
        for (int i = 0; i < ret.length; i++) {
            end = segments[i];
            ret[i] = deviation(timeSeries, start, end);
            start = end;
        }
        return ret;
    }

    //将points分割成两段，生成两倍长的数组，要求每段长度至少为minLength，否则不分割
    public static short[] split(short[] points, short minLength) {
        short[] newPoints = new short[points.length * 2];
        int c = 0;
        for (int i = 0; i < points.length; i++) {
            short length = points[i]; //i==0
            if (i > 0)
                length = (short) (points[i] - points[i - 1]);

            if (length >= minLength * 2) {
                int start = 0;
                if (i > 0)
                    start = points[i - 1];
                newPoints[c++] = (short) (start + length / 2);
            }
            newPoints[c++] = points[i];
        }

        short[] ret = new short[c];
        System.arraycopy(newPoints, 0, ret, 0, ret.length);
        return ret;
    }
}
