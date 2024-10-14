package io.github.utils;

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
    LandmarkPortfolio[] portfolios = null;

    public LandmarkMBR(LandmarkPortfolio portfolio){
        this.portfolios = new LandmarkPortfolio[]{portfolio};
        minDistances = portfolio.getLandmarkDistances();
        maxDistances = portfolio.getLandmarkDistances();
        chosenLandmarks = portfolio.getClosestLandmarks();
    }

    public LandmarkMBR(LandmarkPortfolio[] portfolios) {
        this.portfolios = portfolios;

//        Edge case: if there is only one portfolio, we can use the closest landmark for each dimension
        if (portfolios.length == 1) {
            minDistances = portfolios[0].getLandmarkDistances();
            maxDistances = portfolios[0].getLandmarkDistances();
            chosenLandmarks = portfolios[0].getClosestLandmarks();
            return;
        }

        double[][][] distances = new double[portfolios.length][][];
        for (int i = 0; i < portfolios.length; i++) {
            distances[i] = portfolios[i].getLandmarkDistances();
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
            for (int i = 0; i < portfolios.length; i++) {
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

    public DistanceBound getDistance(LandmarkMBR otherMBR) {
        if (otherMBR.portfolios.length > 1){
            throw new IllegalArgumentException("LandmarkMBR.getDistance() only supports one portfolio");
        }

        return getDistance(otherMBR.portfolios[0]);
    }

    public double minDistance(int d) {
        return minDistances[d][chosenLandmarks[d]];
    }

    public double maxDistance(int d) {
        return maxDistances[d][chosenLandmarks[d]];
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

    /**
     * Combine two LandmarkMBRs into a new LandmarkMBR turning it into a portfolio
     * @param other
     * @return
     */
    public LandmarkMBR add(LandmarkMBR other){
        LandmarkPortfolio[] newPortfolios = new LandmarkPortfolio[portfolios.length + other.portfolios.length];
        System.arraycopy(portfolios, 0, newPortfolios, 0, portfolios.length);
        System.arraycopy(other.portfolios, 0, newPortfolios, portfolios.length, other.portfolios.length);
        return new LandmarkMBR(newPortfolios);
    }

    public static LandmarkMBR merge(List<LandmarkMBR> mbrs) {
        if (mbrs.isEmpty()) {
            return null;
        }

//        Merge all portfolios into a single portfolio
        final int pSize = mbrs.stream().mapToInt(m -> m.portfolios.length).sum();
        LandmarkPortfolio[] newPortfolios = new LandmarkPortfolio[pSize];
        int i = 0;
        for (LandmarkMBR mbr : mbrs) {
            System.arraycopy(mbr.portfolios, 0, newPortfolios, i, mbr.portfolios.length);
            i += mbr.portfolios.length;
        }

        return new LandmarkMBR(newPortfolios);
    }
}
