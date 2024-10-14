package net.jelter.algorithms.mseg;

import lombok.Getter;
import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.utils.*;
import net.jelter.utils.rtreemulti.geometry.Point;
import org.apache.commons.math3.util.FastMath;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.jelter.utils.Parameters.*;

public class JensNode {
    public final int timeSeriesIndex;
    public final int segStart;
    public final int segEnd;
    @Getter private final JensNode left;
    @Getter private final JensNode right;
    final private MinimumBoundingRectangle mbr;
    final private LandmarkMBR landmarkMBR;

    public JensNode(int timeSeriesIndex, int segStart, int segEnd, double[][] dfts, LandmarkPortfolio[] portfolios) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.segStart = segStart;
        this.segEnd = segEnd;
        final int fourierLength = fourierLength(); // Length of the coefficients part

        // We compute MSeg's MBR in this constructor instead of in the MinimumBoundingRectangle MBR for optimization purposes
        // Now we can  efficiently compute this MBR by taking the minimum and maximum values of the MBR of the left and right node
        // Otherwise, we would have to iterate over all items in the range to compute the MBR, which requires end-start+1 iterations instead of 2

        final double[] mins;
        final double[] maxs;

        if (isLeaf()) {
            left = null;
            right = null;

            nLeafs++;

            mins = lib.minArray(fourierLength);
            maxs = lib.maxArray(fourierLength);

            for (int i = 0; i < fourierLength; i++) {
                for (int j = segStart; j <= segEnd; j++) {
                    mins[i] = Math.min(mins[i], dfts[j][i]);
                    maxs[i] = FastMath.max(maxs[i], dfts[j][i]);
                }
            }
        } else {
            nNodes++;

            final int mid = Math.floorDiv(segStart + segEnd, 2);
            left = new JensNode(timeSeriesIndex, segStart, mid, dfts, portfolios);
            right = new JensNode(timeSeriesIndex, mid + 1, segEnd, dfts, portfolios);

            mins = lib.minimum(left.mbr.mins(), right.mbr.mins());
            maxs = lib.maximum(left.mbr.maxes(), right.mbr.maxes());
        }

        this.mbr = new MinimumBoundingRectangle(mins, maxs);

        if (kMeansClusters == 0) {
            landmarkMBR = null;
            return;
        }
        landmarkMBR = new LandmarkMBR(portfolios);
    }

    public JensNode(int timeSeriesIndex, int segStart, int segEnd, double[] mins, double[] maxs, JensNode left, JensNode right, LandmarkMBR landmarkMBR) {
        this.timeSeriesIndex = timeSeriesIndex;
        this.segStart = segStart;
        this.segEnd = segEnd;
        this.mbr = new MinimumBoundingRectangle(mins, maxs);
        this.left = left;
        this.right = right;
        this.landmarkMBR = landmarkMBR;
    }

    public static JensNode deserialize(DataInputStream stream) throws IOException {
        final int coeffLength = fourierLength(); // Length of the coefficients part

        final int timeSeriesIndex = stream.readInt();
        final int segStart = stream.readInt();
        final int segEnd = stream.readInt();

        final double[] mins = new double[coeffLength];
        final double[] maxs = new double[coeffLength];
        for (int i = 0; i < mins.length; i++) {
            mins[i] = stream.readDouble();
            maxs[i] = stream.readDouble();
        }
        final LandmarkMBR landmarkMBR;
        if (kMeansClusters != 0) {
            landmarkMBR = new LandmarkMBR(stream);
        } else {
            landmarkMBR = null;
        }
        if (segEnd - segStart + 1 <= indexLeafSize) {
            return new JensNode(timeSeriesIndex,segStart, segEnd, mins, maxs, null, null, landmarkMBR);
        } else {
            final JensNode left = deserialize(stream);
            final JensNode right = deserialize(stream);
            return new JensNode(timeSeriesIndex,segStart, segEnd, mins, maxs, left, right, landmarkMBR);
        }
    }

    public boolean isLeaf() {
        return segEnd - segStart + 1 <= indexLeafSize;
    }

    public CandidateSegment toCandidateSegment() {
        CandidateSegment candidateSegment = new CandidateSegment(timeSeriesIndex, segStart, segEnd, mbr);
        return candidateSegment;
    }

    //    Query this for interesting segments
    public List<CandidateSegment> queryBranchRecursive(MinimumBoundingRectangle queryMBR, double threshold) {
        double LB = mbr.distance(queryMBR);

        ArrayList<CandidateSegment> out = new ArrayList<>(0);

        if (LB >= threshold) { // decisive negative
            return out;
        } else if (isLeaf()) { // leaf node
            out.add(toCandidateSegment());
            return out;
        }

        // need to split further to get conclusive answer
        out.addAll(left.queryBranchRecursive(queryMBR, threshold));
        out.addAll(right.queryBranchRecursive(queryMBR, threshold));

        return out;
    }

    public void serialize(DataOutputStream stream) {
        try {
            stream.writeInt(timeSeriesIndex);
            stream.writeInt(segStart);
            stream.writeInt(segEnd);
            for (int i = 0; i < mbr.dimensions(); i++) {
                stream.writeDouble(mbr.mins()[i]);
                stream.writeDouble(mbr.maxes()[i]);
            }
            if (landmarkMBR != null) {
                landmarkMBR.serialize(stream);
            }
            if (!isLeaf()) {
                left.serialize(stream);
                right.serialize(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
