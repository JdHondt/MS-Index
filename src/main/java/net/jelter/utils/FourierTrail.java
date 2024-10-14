package net.jelter.utils;

import lombok.*;

@Setter
@Getter
public class FourierTrail {
    private double[][] trail;

    private LandmarkPortfolio[] landmarkPortfolios;

    public FourierTrail(double[][] trail, LandmarkPortfolio[] landmarkPortfolios) {
        this.trail = trail;
        this.landmarkPortfolios = landmarkPortfolios;
    }

    public int getLength() {
        return trail.length;
    }
}
