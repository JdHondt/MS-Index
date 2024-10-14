package net.jelter.algorithms.multistindex.segmentation;

import lombok.Builder;
import lombok.NonNull;
import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.utils.CandidateSegment;
import net.jelter.utils.FourierTrail;
import net.jelter.utils.LandmarkMBR;
import net.jelter.utils.LandmarkPortfolio;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.geometry.Geometry;
import net.jelter.utils.rtreemulti.geometry.Point;

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
