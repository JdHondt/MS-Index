package io.github.algorithms.msindex.segmentation;

import io.github.utils.*;
import lombok.Builder;
import lombok.NonNull;
import io.github.algorithms.msindex.MinimumBoundingRectangle;
import io.github.utils.*;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.util.Collections;
import java.util.List;

@Builder
public class NoSegment extends Segmentation {
    @NonNull private boolean withMBR;

    /**
     * Singleton segmentation of the time series. Used to be equi-depth with volume 0 but this is optimized for this case.
     */
    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        final double[][] trail = fourierTrail.getTrail();
        final LandmarkPortfolio[] portfolios = fourierTrail.getLandmarkPortfolios();

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        final LandmarkMBR landmarkMBR = new LandmarkMBR(portfolios);

        final double[] mins = lib.minimum(trail);
        final double[] maxs = lib.maximum(trail);
        final MinimumBoundingRectangle mbr = MinimumBoundingRectangle.create(mins, maxs, landmarkMBR);

        return Collections.singletonList(Entry.entry(new CandidateSegment(idx, 0, trail.length - 1, mbr), mbr));
    }

}
