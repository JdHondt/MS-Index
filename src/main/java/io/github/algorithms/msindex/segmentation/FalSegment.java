package io.github.algorithms.msindex.segmentation;

import io.github.utils.*;
import io.github.io.DataManager;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.geometry.Geometry;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.utils.Parameters.*;

public class FalSegment extends Segmentation {
    /**
     * Segment the MTS using the segmentation algorithm from the faloutsos94 paper.
     */
    public List<Entry<CandidateSegment, Geometry>> segment(int timeSeries, FourierTrail fourierTrail) {
        double[][] trail = fourierTrail.getTrail();
        LandmarkPortfolio[] landmarkPortfolios = fourierTrail.getLandmarkPortfolios();

        final int noCoeffsPerDimension = Parameters.fourierLengthPerDimension();

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
            if (dimensionToSegmentOn == channels) {
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
            entries.add(createSubEntry(trail, timeSeries, start, i-1, landmarkPortfolios));

//            Reset counters and MBRs
            segMinNorm = normFourier;
            segMaxNorm = normFourier;

            start = i;
            oldRatio = Double.MAX_VALUE;
        }

//        Get the last segment
        entries.add(createSubEntry(trail, timeSeries, start, trail.length - 1, landmarkPortfolios));
        return entries;
    }
}
