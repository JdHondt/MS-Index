package io.github.algorithms.msindex.segmentation;

import io.github.utils.*;
import io.github.utils.rtreemulti.Entry;
import io.github.algorithms.msindex.MinimumBoundingRectangle;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.util.Arrays;
import java.util.List;

public abstract class Segmentation {

    public static Segmentation get(SegmentMethods method) {
        switch (method) {
            case GREEDY:
                return new GreedySegment();
            case FALOUTSOS:
                return new FalSegment();
            case EQUI_LENGTH:
                return new EquiLengthSegment();
            case EQUI_DEPTH:
                return new EquiDepthSegment();
            case ADHOC:
                return new AdhocSegment();
            case SINGLETON:
                return new SingletonSegment(true);
            case NONE:
                return new NoSegment(true);
            default:
                throw new IllegalArgumentException("Invalid method");
        }
    }




    public abstract List<Entry<CandidateSegment, Geometry>> segment(int timeSeries, FourierTrail fourierTrail);

    /**
     * Create a tree entry from a segment of the time series.
     *
     * @param subtrail:          The fourier trail of the time series
     * @param start:         start of the segment
     * @param end:           (inclusive) end of the segment
     * @param tsIdx:         The index of the time series in the dataset
     * @return : An entry containing the segment and its corresponding MBR
     */
    private static Entry<CandidateSegment, Geometry> createTreeEntry(double[][] subtrail,
                                                                                    int tsIdx, int start, int end,
                                                                                    LandmarkPortfolio[] subPortfolios) {
//        Checks
        final int length = end - start + 1;
        if (length < 1) {
            throw new IllegalArgumentException("Segment length must be at least 1");
        }
        if (subtrail.length != length) {
            throw new IllegalArgumentException("Length of dfts must be equal to the segment length");
        }
        if (subPortfolios != null && subtrail.length != subPortfolios.length) {
            throw new IllegalArgumentException("Length of portfolios must be equal to the segment length");
        }
        //        Edge-case
        if (length == 1) {
            final double[] point = subtrail[0];
            final LandmarkMBR landmarkMBR = subPortfolios == null ? null: new LandmarkMBR(subPortfolios[0]);
            final MinimumBoundingRectangle mbr = new MinimumBoundingRectangle(point, point, landmarkMBR);
            return Entry.entry(new CandidateSegment(tsIdx, start, end, mbr), mbr);
        }

//        Initialize the MBR
        final double[] segMin = lib.minimum(subtrail);
        final double[] segMax = lib.maximum(subtrail);

        final LandmarkMBR landmarkMBR = subPortfolios == null ? null: new LandmarkMBR(subPortfolios, 0, subPortfolios.length);

//        Create the entry
        final MinimumBoundingRectangle mbr = new MinimumBoundingRectangle(segMin, segMax, landmarkMBR);
        return Entry.entry(new CandidateSegment(tsIdx, start, end, mbr), mbr);
    }

    public static Entry<CandidateSegment, Geometry> createSubEntry(double[][] trail,
                                                                                    int tsIdx, int start, int end,
                                                                                    LandmarkPortfolio[] allPortfolios) {
//        Get subset of the trail
        double[][] subtrail = Arrays.copyOfRange(trail, start, end + 1);

        LandmarkPortfolio[] subPortfolios = allPortfolios == null ? null: Arrays.copyOfRange(allPortfolios, start, end + 1);

//                Add the segment to the list
        return createTreeEntry(subtrail, tsIdx, start, end, subPortfolios);
    }
}
