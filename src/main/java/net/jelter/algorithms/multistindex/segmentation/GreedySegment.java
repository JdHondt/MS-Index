package net.jelter.algorithms.multistindex.segmentation;

import net.jelter.utils.CandidateSegment;
import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.utils.FourierTrail;
import net.jelter.utils.LandmarkPortfolio;
import net.jelter.utils.Tuple3;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.geometry.Geometry;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

import static net.jelter.utils.Parameters.mstSegmentParameter;

public class GreedySegment extends Segmentation {
    public List<Entry<CandidateSegment, Geometry>> segment(int idx, FourierTrail fourierTrail) {
        double[][] trail = fourierTrail.getTrail();
        LandmarkPortfolio[] landmarkPortfolios = fourierTrail.getLandmarkPortfolios();

        if (trail.length == 0) {
            return Collections.emptyList();
        }

        final int noSegments = (int) FastMath.min(mstSegmentParameter, trail.length);

//        Create the running segmentation (map with start index -> MBR), and set all to true
        final HashMap<Integer, MinimumBoundingRectangle> segmentation = new HashMap<>(trail.length);

//        Initialize the segmentation
        for (int i = 0; i < trail.length; i++) {
            segmentation.put(i, new MinimumBoundingRectangle(trail[i], trail[i]));
        }

//        Create the priority queue containing the volume increases when combining two segments (i, i+1), sorted ascendingly
//        Key: Tuple3<[start1, start2, end incl], resulting MBR, volume increase>
        final PriorityBlockingQueue<Tuple3<int[], MinimumBoundingRectangle, Double>> queue =
                new PriorityBlockingQueue<>(trail.length, Comparator.comparingDouble(Tuple3::_3));

//        Initialize the priority queue
        int i = 0;
        MinimumBoundingRectangle segi = segmentation.get(i);
        while (i < trail.length - 1) {
            MinimumBoundingRectangle segi1 = segmentation.get(i + 1);
            MinimumBoundingRectangle merge = segi.add(segi1);
            double volumeIncrease = merge.volume() - segi.volume() - segi1.volume();
            queue.add(Tuple3.of(new int[]{i, i + 1, i + 1}, merge, volumeIncrease));

//            Prep for next iteration
            segi = segi1;
            i++;
        }


//        Main loop; merge the two segments with the smallest volume increase until we have the desired number of segments
        int card = segmentation.size();
        while (card > noSegments) {
            Tuple3<int[], MinimumBoundingRectangle, Double> merge = queue.poll();
            int[] indices = merge._1();
            int start1 = indices[0];
            int start2 = indices[1];
            int endIncl = indices[2];
            MinimumBoundingRectangle toMergeMBR = merge._2();

//            Check if the merge is still valid (i.e., the segments have not been merged with other segments in the meantime),
            boolean containsS1 = segmentation.containsKey(start1);
            boolean containsS2 = segmentation.containsKey(start2);
            boolean containsE = endIncl == trail.length - 1 || segmentation.containsKey(endIncl + 1);
            if (containsS1 && containsS2 && containsE) {
                //            Update the segmentation
                segmentation.remove(start2);
                segmentation.put(start1, toMergeMBR);
                card--;

//                Logger.getGlobal().info("[MFaloutsos] Created segment from " + start1 + " to " + endIncl + " with volume increase: " + merge._3());
//                Logger.getGlobal().info("[MFaloutsos] Total volume now: " + segmentation.values().stream().mapToDouble(MinimumBoundingRectangle::volume).sum());
            }
            // The segments have been changed in the meantime, let's see if we should create a new proposal
            else if (!containsS1) continue;
            else if (!containsS2) {
                throw new IllegalStateException("Segmentation is invalid; segment " + start2 + " has been merged with another segment which is not possible");
            }

            // Create new merge proposals if possible
            if (card > noSegments && endIncl < trail.length - 1) {
                if (containsE) {
                    start2 = endIncl + 1;
                }

//                Find the end of the next segment
                endIncl++;
                if (endIncl < trail.length) { // We're still not at the end of the time series
//                    Iterate until we find the beginning of the next segment
                    while (endIncl < trail.length - 1 && !segmentation.containsKey(endIncl + 1)) {
                        endIncl++;
                    }
                } else { // We're at the end of the time series, so this is the last (singleton) segment
                    endIncl = trail.length - 1;
                }

//                Create the new merge proposal
                MinimumBoundingRectangle newSegment = segmentation.get(start2);
                MinimumBoundingRectangle newMerge = toMergeMBR.add(newSegment);
                double newVolumeIncrease = newMerge.volume() - toMergeMBR.volume() - newSegment.volume();
                queue.add(Tuple3.of(new int[]{start1, start2, endIncl}, newMerge, newVolumeIncrease));
            }
        }

//        Check if first segment is not empty
        if (!segmentation.containsKey(0)) {
            throw new IllegalStateException("Something went wrong in the greedy segmentation; first segment does not start at 0");
        }

//        Create the entries
        final ArrayList<Entry<CandidateSegment, Geometry>> entries = new ArrayList<>(segmentation.size());
        int start = 0;
        for (int end = 1; end < trail.length; end++) {
            if (!segmentation.containsKey(end)) {
                continue;
            }


            entries.add(createSubEntry(trail, idx, start, end - 1, landmarkPortfolios));
            start = end;
        }

//        Add the last segment
        entries.add(createSubEntry(trail, idx, start, trail.length - 1, landmarkPortfolios));
        return entries;
    }
}
