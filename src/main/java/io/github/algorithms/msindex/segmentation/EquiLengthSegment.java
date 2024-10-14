package io.github.algorithms.msindex.segmentation;

import io.github.utils.FourierTrail;
import io.github.utils.LandmarkPortfolio;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.CandidateSegment;
import io.github.utils.rtreemulti.geometry.Geometry;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.utils.Parameters.segmentParameter;

public class EquiLengthSegment extends Segmentation {
    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        final double[][] trail = fourierTrail.getTrail();
        final LandmarkPortfolio[] landmarkPortfolios = fourierTrail.getLandmarkPortfolios();

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        int noSegments = (int) FastMath.min(segmentParameter, trail.length);
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
