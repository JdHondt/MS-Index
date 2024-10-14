package net.jelter.algorithms.stindex;

import com.github.davidmoten.rtreemulti.geometry.Point;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.algorithms.multistindex.segmentation.Segmentation;
import net.jelter.io.DataManager;
import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import net.jelter.utils.*;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.jelter.utils.Parameters.dimensions;

public class DavidFalSegment  {
    /**
     * Segment the MTS using the segmentation algorithm from the faloutsos94 paper.
     */
    public static List<Entry<CandidateSegment, Geometry>> segment(int timeSeries, FourierTrail fourierTrail, int dimension) {
        double[][] fullTrail = fourierTrail.getTrail();

        final int noCoeffsPerDimension = Parameters.fourierLengthPerDimension();

//        Limit trail to the relevant dimension
        double[][] trail = new double[fullTrail.length][noCoeffsPerDimension];
        for (int i = 0; i < fullTrail.length; i++) {
            System.arraycopy(fullTrail[i], noCoeffsPerDimension * dimension, trail[i], 0, noCoeffsPerDimension);
        }

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        final ArrayList<Entry<CandidateSegment, Geometry>> entries = new ArrayList<>();

//        Derive the total mins and maxs for the normalization
        double[] totalMins = lib.minArray(noCoeffsPerDimension);
        double[] totalMaxs = lib.maxArray(noCoeffsPerDimension);

        for (double[] doubles : trail) {
            for (int j = 0; j < noCoeffsPerDimension; j++) {
                totalMins[j] = FastMath.min(totalMins[j], doubles[j]);
                totalMaxs[j] = FastMath.max(totalMaxs[j], doubles[j]);
            }
        }

//        Initialize the running variables for the segmentation
        double[] segMinNorm = lib.minArray(noCoeffsPerDimension);
        double[] segMaxNorm = lib.maxArray(noCoeffsPerDimension);
        double oldRatio = Double.MAX_VALUE;
        int start = 0;

        for (int i = 0; i < trail.length; i++) {
            double currentRatio = 1;

            int dimensionToSegmentOn = 0;

            // Multi ST-index should be forced to segment on the first available variate
            while (!DataManager.supportsVariate(timeSeries, dimensionToSegmentOn)) {
                dimensionToSegmentOn++;
            }
            if (dimensionToSegmentOn == dimensions) {
                return entries;
            }

//            Get the normalized values of the current segment, ONLY FOR FIRST AVAILABLE VARIATE
            final double[] normFourier = new double[noCoeffsPerDimension];
            for (int j = 0; j < noCoeffsPerDimension; j++) {
                normFourier[j] = lib.minMaxNormalize(trail[i][noCoeffsPerDimension * dimensionToSegmentOn + j], totalMins[j], totalMaxs[j]);
            }

//            Update the MBR of the current segment.
            segMinNorm = lib.minimum(segMinNorm, normFourier);
            segMaxNorm = lib.maximum(segMaxNorm, normFourier);

//           Update current ratio
            for (int k = 0; k < segMinNorm.length; k++) {
                currentRatio *= (segMaxNorm[k] - segMinNorm[k]) + .5;
            }
            currentRatio /= i - start + 1;

//            Compute if we should start new segment or continue the current one.
            if (currentRatio < oldRatio) { // continue the current segment
                oldRatio = currentRatio;
                continue;
            }

//                Add the segment to the list
            entries.add(createSubEntry(trail, timeSeries, start, i-1));

//            Reset counters and MBRs
            segMinNorm = normFourier;
            segMaxNorm = normFourier;

            start = i;
            oldRatio = Double.MAX_VALUE;
        }

//        Get the last segment
        entries.add(createSubEntry(trail, timeSeries, start, trail.length - 1));
        return entries;
    }

    private static Entry<CandidateSegment, Geometry> createSubEntry(double[][] trail, int tsIdx, int start, int end) {
//        Get subset of the trail
        double[][] subtrail = Arrays.copyOfRange(trail, start, end + 1);

//                Add the segment to the list
        return createTreeEntry(subtrail, tsIdx, start, end);
    }

    private static Entry<CandidateSegment, Geometry> createTreeEntry(double[][] subtrail,int tsIdx, int start, int end) {
//        Checks
        final int length = end - start + 1;
        if (length < 1) {
            throw new IllegalArgumentException("Segment length must be at least 1");
        }
        if (subtrail.length != length) {
            throw new IllegalArgumentException("Length of dfts must be equal to the segment length");
        }

        //        Edge-case
        if (length == 1) {
            final double[] point = subtrail[0];
            final Point pointobj = Point.create(point);
            return Entry.entry(new CandidateSegment(tsIdx, start, end, null), pointobj);
        }

//        Initialize the MBR
        final double[] segMin = lib.minimum(subtrail);
        final double[] segMax = lib.maximum(subtrail);

//        Create the entry
        final Rectangle rec = Rectangle.create(segMin, segMax);
        return Entry.entry(new CandidateSegment(tsIdx, start, end, null), rec);
    }
}
