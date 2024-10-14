package net.jelter.utils;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

public class LandmarkPortfolio implements Serializable {

    @NonNull @Getter private final double[][] landmarkDistances; // shape: [dimensions][nrLandmarks]
    @Getter private final int[] closestLandmarks; // shape: [dimensions]
    private double[] closestDistances = null;

    public LandmarkPortfolio(double[][] landmarkDistances) {
        this.landmarkDistances = landmarkDistances;

//        Find the closest landmark for each dimension
        this.closestLandmarks = new int[landmarkDistances.length];
        for (int i = 0; i < landmarkDistances.length; i++) {
            this.closestLandmarks[i] = lib.argmin(landmarkDistances[i]);
        }
    }

    public double[] getDistances(int dimension) {
        return landmarkDistances[dimension];
    }

    public double getDistance(int dimension, int landmark) {
        return landmarkDistances[dimension][landmark];
    }

    public int getClosestLandmark(int dimension) {
        return closestLandmarks[dimension];
    }

    public double[] getClosestDistances() {
        if (closestDistances == null) {
            closestDistances = new double[landmarkDistances.length];
            for (int i = 0; i < closestDistances.length; i++) {
                closestDistances[i] = getDistance(i, getClosestLandmark(i));
            }
        }
        return closestDistances;
    }


}
