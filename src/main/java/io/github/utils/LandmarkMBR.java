package io.github.utils;

import lombok.Setter;
import org.apache.commons.math3.util.FastMath;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import static io.github.utils.Parameters.*;
import static io.github.utils.lib.minimum;
import static io.github.utils.lib.pow2;

public class LandmarkMBR implements Serializable {

    final double[][] minDistances; // shape: [dimensions, #landmarks]
    final double[][] maxDistances; // shape: [dimensions, #landmarks]
    final int[] chosenLandmarks;
    @Setter LandmarkPortfolio leafPortfolio;

    public LandmarkMBR(LandmarkPortfolio portfolio){
        minDistances = portfolio.getLandmarkDistances();
        maxDistances = portfolio.getLandmarkDistances();
        chosenLandmarks = portfolio.getClosestLandmarks();
        leafPortfolio = portfolio;
    }

    public LandmarkMBR(double[][] minDistances, double[][] maxDistances, int[] chosenLandmarks) {
        this.minDistances = minDistances;
        this.maxDistances = maxDistances;
        this.chosenLandmarks = chosenLandmarks;
    }

    public LandmarkMBR(LandmarkPortfolio[] portfolios, int start, int end) {
        if (portfolios.length == 0) {
            throw new IllegalArgumentException("Cannot create an MBR from an empty list of portfolios.");
        }

        if (start < 0 || end > portfolios.length) {
            throw new IllegalArgumentException("Invalid start or end index.");
        }

//        Edge case: if there is only one portfolio, we can use the closest landmark for each dimension
        if (portfolios.length == 1) {
            minDistances = portfolios[0].getLandmarkDistances();
            maxDistances = portfolios[0].getLandmarkDistances();
            chosenLandmarks = portfolios[0].getClosestLandmarks();
            return;
        }

        final int n = end - start;

        double[][][] distances = new double[n][][]; // shape: [nPortfolios][dimensions][#landmarks]
        for (int i = 0; i < n; i++) {
            distances[i] = portfolios[i + start].getLandmarkDistances();
        }
        minDistances = lib.minimum(distances);
        maxDistances = lib.maximum(distances);
        chosenLandmarks = new int[channels];

//        For each dimension, find the landmark that is closest to most points, AND set the min and max distances
        for (int d = 0; d < channels; d++) {
            if (portfolios[0].getDistances(d) == null) {
                continue;
            }

//            Count the number of points that are closest to each landmark
            final int[] counts = new int[kMeansClusters];
            for (int i = start; i < end; i++) {
                final int closestLandmark = portfolios[i].getClosestLandmark(d);
                counts[closestLandmark]++;
            }

//            Find the landmark that is closest to most points
            final int chosen = lib.argmax(counts);
            chosenLandmarks[d] = chosen;

        }
    }

    public LandmarkMBR(DataInputStream stream) {
        minDistances = new double[channels][kMeansClusters];
        maxDistances = new double[channels][kMeansClusters];
        chosenLandmarks = new int[channels];
        try {
            for (int i = 0; i < channels; i++) {
                minDistances[i] = new double[kMeansClusters];
                maxDistances[i] = new double[kMeansClusters];
                for (int j = 0; j < kMeansClusters; j++) {
                    minDistances[i][j] = stream.readDouble();
                    maxDistances[i][j] = stream.readDouble();
                }
                chosenLandmarks[i] = stream.readInt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static LandmarkMBR merge(List<LandmarkMBR> mbrs) {
        if (mbrs.isEmpty()) {
            return null;
        }

//        Iteratively merge the MBRs
        LandmarkMBR merged = mbrs.get(0);
        for (int i = 1; i < mbrs.size(); i++) {
            merged = merge(merged, mbrs.get(i));
        }
        return merged;
    }

    /**
     * Merge two MBRs by deriving the minimum and maximum from their min and max distances
     * @param left MBR
     * @param right MBR
     * @return Merged MBR
     */
    public static LandmarkMBR merge(LandmarkMBR left, LandmarkMBR right) {
        double[][] minDistances = new double[channels][kMeansClusters];
        double[][] maxDistances = new double[channels][kMeansClusters];
        int[] chosenLandmarks = new int[channels];
        for (int d = 0; d < channels; d++) {
            for (int i = 0; i < kMeansClusters; i++) {
                minDistances[d][i] = FastMath.min(left.minDistances[d][i], right.minDistances[d][i]);
                maxDistances[d][i] = FastMath.max(left.maxDistances[d][i], right.maxDistances[d][i]);
            }
            chosenLandmarks[d] = left.minDistance(d) < right.minDistance(d) ? left.chosenLandmarks[d] : right.chosenLandmarks[d];
        }
        return new LandmarkMBR(minDistances, maxDistances, chosenLandmarks);
    }

    public double minDistance(int d) {
        return minDistances[d][chosenLandmarks[d]];
    }

    public double maxDistance(int d) {
        return maxDistances[d][chosenLandmarks[d]];
    }

    public DistanceBound getDistance(LandmarkMBR otherMBR) {
        if (otherMBR.leafPortfolio == null){
            throw new IllegalArgumentException("The other MBR does not have a leaf portfolio, which is required.");
        }

        return getDistance(otherMBR.leafPortfolio);
    }

    public DistanceBound getDistance(LandmarkPortfolio queryPortfolio) {
        double LB = 0;
        double UB = 0;
        for (int d : selectedVariatesIdx) {
            final double queryLandmarkDist = queryPortfolio.getDistance(d, chosenLandmarks[d]);

            final double minDiff = queryLandmarkDist - minDistance(d);
            final double minDiff2 = pow2(minDiff);

            final double maxDiff = queryLandmarkDist - maxDistance(d);
            final double maxDiff2 = pow2(maxDiff);

            if (minDiff < 0) {
                LB += minDiff2;
                UB += maxDiff2;
            } else if (maxDiff > 0) {
                LB += maxDiff2;
                UB += minDiff2;
            } else {
                LB += 0;
                UB += FastMath.max(minDiff2, maxDiff2);
            }
        }
        return new DistanceBound(LB, UB);
    }

    public void serialize(DataOutputStream stream) throws IOException {
        for (int i = 0; i < channels; i++) {
            for (int j = 0; j < kMeansClusters; j++) {
                stream.writeDouble(minDistances[i][j]);
                stream.writeDouble(maxDistances[i][j]);
            }
            stream.writeInt(chosenLandmarks[i]);
        }
    }


}
