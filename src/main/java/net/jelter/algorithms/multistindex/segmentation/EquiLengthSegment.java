package net.jelter.algorithms.multistindex.segmentation;

import net.jelter.utils.FourierTrail;
import net.jelter.utils.LandmarkPortfolio;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.CandidateSegment;
import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.utils.rtreemulti.geometry.Geometry;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.jelter.utils.Parameters.mstSegmentParameter;

public class EquiLengthSegment extends Segmentation {
    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        final double[][] trail = fourierTrail.getTrail();
        final LandmarkPortfolio[] landmarkPortfolios = fourierTrail.getLandmarkPortfolios();

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        int noSegments = (int) FastMath.min(mstSegmentParameter, trail.length);
        final int segmentLength = (int) Math.ceil((trail.length / (double) noSegments));
        noSegments = (int) Math.ceil(trail.length / (double) segmentLength);
        final ArrayList<Entry<CandidateSegment, Geometry>> entries = new ArrayList<>(noSegments);

//        Create entries of the segments
        for (int i = 0; i < noSegments; i++) {
            int start = i * segmentLength;
            int end = FastMath.min((i + 1) * segmentLength - 1, trail.length - 1);

//                Add the segment to the list
            entries.add(createSubEntry(trail, idx, start, end, landmarkPortfolios));
        }
        return entries;
    }
}
