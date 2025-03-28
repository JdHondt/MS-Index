package io.github.utils;

import lombok.Getter;
import io.github.algorithms.msindex.MinimumBoundingRectangle;
import org.apache.commons.math3.util.FastMath;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static io.github.utils.Parameters.*;

@Getter
public class CandidateSegment implements Serializable {
    private final int timeSeriesIndex;
    private final Range segmentRange;
    private final MinimumBoundingRectangle mbr;

//    START: start of segment
//    END: starting point of the last qlen subsequence of the segment!!
    public CandidateSegment(int timeSeriesIndex, int start, int end, MinimumBoundingRectangle mbr) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.segmentRange = new Range(start, end);
        this.mbr = mbr;
    }

    @Override
    public String toString() {
        return "CandidateSegment{" +
                "timeSeriesIndex=" + timeSeriesIndex +
                ", segmentRange=" + segmentRange +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CandidateSegment)) return false;

        CandidateSegment candidateSegment = (CandidateSegment) o;

        if (timeSeriesIndex != candidateSegment.timeSeriesIndex) return false;
        return segmentRange.equals(candidateSegment.segmentRange);
    }

//    First compare on the time series index, then on the segment range
    public int compareTo(CandidateSegment other) {
        if (this.timeSeriesIndex != other.timeSeriesIndex) {
            return Integer.compare(this.timeSeriesIndex, other.timeSeriesIndex);
        }
        return this.segmentRange.compareTo(other.segmentRange);
    }

    public int getStart() {
        return segmentRange.getStart();
    }

    public int getEnd() {
        return segmentRange.getEnd();
    }

    public int getLength() {
        return segmentRange.getLength();
    }

    public boolean neighbours(CandidateSegment other) {
        return this.segmentRange.neighbours(other.segmentRange);
    }

    public double getCurrLB() {
        return mbr.distanceCache[currQueryId];
    }

    public static CandidateSegment merge(List<CandidateSegment> segments, boolean newMBRs) {
        if (segments.isEmpty()) {
            return null;
        }

//        Get new start, end and MBR
        int newStart = Integer.MAX_VALUE;
        int newEnd = Integer.MIN_VALUE;
        for (CandidateSegment segment : segments) {
            newStart = FastMath.min(newStart, segment.segmentRange.getStart());
            newEnd = FastMath.max(newEnd, segment.segmentRange.getEnd());
        }

//        Merge the landmarkMBRs
        MinimumBoundingRectangle newMBR = null;
        if (newMBRs) {
            newMBR = segments.stream().map(s -> s.mbr).reduce(MinimumBoundingRectangle::add).get();
            newMBR.setLandmarkMBR(LandmarkMBR.merge(segments.stream().map(s -> s.mbr.getLandmarkMBR()).collect(Collectors.toList())));
        }

        return new CandidateSegment(segments.get(0).timeSeriesIndex, newStart, newEnd, newMBR);
    }

//    ------------------- Static methods -------------------
    public static Map<Integer, List<CandidateSegment>> groupByTimeSeriesIndex(List<CandidateSegment> candidateSegments) {
        return candidateSegments.stream().collect(Collectors.groupingBy(CandidateSegment::getTimeSeriesIndex));
    }

//    Sort the candidate segments by time series index and segment range, then merge overlapping segments
    public static ArrayList<CandidateSegment> mergeCandidateSegments(List<CandidateSegment> candidateSegments, boolean newMBRs) {
        LinkedList<CandidateSegment> out = new LinkedList<>();

//        Group the candidate segments by time series index
        Map<Integer, List<CandidateSegment>> grouped = groupByTimeSeriesIndex(candidateSegments);

        for (List<CandidateSegment> segments : grouped.values()) {


//            Sort the segments by segment range
            segments.sort(CandidateSegment::compareTo);

//            Group the segments that overlap
            ArrayList<CandidateSegment> group = new ArrayList<>(segments.size());
            for (CandidateSegment segment : segments) {
                if (group.isEmpty()) {
                    group.add(segment);
                    continue;
                }
                CandidateSegment last = group.get(group.size() - 1);
                if (last.neighbours(segment)) { // Add to the group if the segments overlap
                    group.add(segment);
                } else { // Merge the group and add it to the output
                    out.add(merge(group, newMBRs));
                    group.clear();
                    group.add(segment);
                }
            }
//            Merge the last group
            if (!group.isEmpty()) {
                out.add(merge(group, newMBRs));
            }
        }



        return new ArrayList<>(out);
    }

    public boolean contains(int timeSeriesIndex, int segmentId) {
        return this.timeSeriesIndex == timeSeriesIndex && this.segmentRange.getStart() <= segmentId && this.segmentRange.getEnd() >= segmentId;
    }
};
