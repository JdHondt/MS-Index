package net.jelter.algorithms.multistindex.segmentation;

import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.utils.CandidateSegment;
import net.jelter.utils.FourierTrail;
import net.jelter.utils.LandmarkPortfolio;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.jelter.utils.Parameters.mstSegmentParameter;

public class EquiDepthSegment extends Segmentation {

    /**
     * Segment the time series using the equi-depth segmentation method.
     * This means that we have a fixed mbr margin for each segment, which is indicated by the mstNrSegments parameter.
     */
    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        final double[][] trail = fourierTrail.getTrail();
        final LandmarkPortfolio[] landmarkPortfolios = fourierTrail.getLandmarkPortfolios();

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        final ArrayList<Entry<CandidateSegment, Geometry>> entries = new ArrayList<>(trail.length);

//        Create segments by iterating over the trail, adding points to the segment until we reach the desired margin
        int start = 0;
        int end = 0;
        MinimumBoundingRectangle runningMBR = new MinimumBoundingRectangle(trail[0], trail[0]);
        double targetRelMargin = mstSegmentParameter;
        while (end < trail.length) {
//            System.out.printf("[%d] %d-%d, running margin: %.2f%n", idx, start, end, runningMBR.margin());
            if (runningMBR.margin() >= targetRelMargin || end == trail.length - 1) {
                entries.add(createSubEntry(trail, idx, start, end, landmarkPortfolios));

                if (end == trail.length - 1) {
                    break;
                }
//                Start a new segment
                start = end + 1;
                end = start;
                runningMBR = new MinimumBoundingRectangle(trail[start], trail[start]);
            } else {
                end++;
                runningMBR = runningMBR.add(new MinimumBoundingRectangle(trail[end], trail[end]));
            }
        }
        return entries;


    }
}
