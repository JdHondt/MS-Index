package io.github.algorithms.msindex.segmentation;

import lombok.Builder;
import lombok.NonNull;
import io.github.algorithms.msindex.MinimumBoundingRectangle;
import io.github.utils.CandidateSegment;
import io.github.utils.FourierTrail;
import io.github.utils.LandmarkMBR;
import io.github.utils.LandmarkPortfolio;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Builder
public class SingletonSegment extends Segmentation {
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

        final ArrayList<Entry<CandidateSegment, Geometry>> entries = new ArrayList<>(trail.length);

        for (int i = 0; i < trail.length; i++) {
            double[] point = trail[i];

            LandmarkMBR landmarkMBR = null;
            if (withMBR) landmarkMBR = new LandmarkMBR(portfolios[i]);
            MinimumBoundingRectangle mbr = MinimumBoundingRectangle.create(point, point, landmarkMBR);
            CandidateSegment segment = new CandidateSegment(idx, i, i, mbr);
            entries.add(Entry.entry(segment, mbr));
        }

        return entries;
    }

}
