package net.jelter.algorithms.multistindex.segmentation;

import com.google.common.util.concurrent.AtomicDouble;
import net.jelter.utils.*;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.RTree;
import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static net.jelter.utils.Parameters.*;

public class AdhocSegment extends Segmentation {
    static FourierTrail[] fourierTrails = new FourierTrail[N]; // Fourier trails for each time series

    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        fourierTrails[idx] = fourierTrail;

//        Run nosegment without MBR creation
        return SingletonSegment.builder().withMBR(false).build().segment(idx, fourierTrail);
    }


    /**
     * For each leaf, combine the neighbouring subsequences from the same time series into a single segment
     * @param tree: the RTree containing the segments
     */
    public static void postIndexSegment(RTree<CandidateSegment, Geometry> tree) {
        nSegments = new AtomicLong(0);

        lib.getStream(tree.leafs()).forEach(leaf -> {
            //            Get entries
            final List<Entry<CandidateSegment, Geometry>> entries = leaf.entries();
            final List<CandidateSegment> keys = entries.stream().map(Entry::value).collect(Collectors.toList());

//            Merge candidate segments
            final List<CandidateSegment> segments = CandidateSegment.mergeCandidateSegments(keys, false);

//            Create entries for the new segments
            final List<CandidateSegment> newEntries = new ArrayList<>(segments.size());
            for (CandidateSegment segment : segments) {
                FourierTrail fourierTrail = fourierTrails[segment.getTimeSeriesIndex()];
                final double[][] trail = fourierTrail.getTrail();
                final LandmarkPortfolio[] portfolios = fourierTrail.getLandmarkPortfolios();
                Entry<CandidateSegment, Geometry> entry = createSubEntry(trail, segment.getTimeSeriesIndex(), segment.getStart(), segment.getEnd(), portfolios);
                newEntries.add(entry.value());
            }

            nSegments.addAndGet(newEntries.size());

//            set new entries
            leaf.setEntries(newEntries.stream().map(e -> Entry.entry(e, (Geometry) e.getMbr())).collect(Collectors.toList()));
        });
    }
}
