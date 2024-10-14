package net.jelter.algorithms.msindex.segmentation;

import lombok.Builder;
import lombok.NonNull;
import net.jelter.algorithms.msindex.MinimumBoundingRectangle;
import net.jelter.utils.*;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.geometry.Geometry;

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
