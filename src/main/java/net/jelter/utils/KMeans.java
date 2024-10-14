package net.jelter.utils;

import org.apache.commons.math3.util.FastMath;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import static net.jelter.utils.Parameters.*;

public class KMeans {
    private final double[][] centroids = new double[kMeansClusters][];
    private int[] assignments;
    private final int maxIter;

    public KMeans(int maxIter) {
        this.maxIter = maxIter;
    }

    public KMeans() {
        this.maxIter = 100;
    }

    public static KMeans deserialize(DataInputStream stream) {
        try {
            KMeans kMeans = new KMeans();
            for (int i = 0; i < kMeansClusters; i++) {
                kMeans.centroids[i] = new double[qLen];
                for (int j = 0; j < qLen; j++) {
                    kMeans.centroids[i][j] = stream.readDouble();
                }
            }
            return kMeans;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void run(double[][] data) {
        assignments = new int[data.length];

        initializeCentroids(data);

        boolean changed;
        int iter = 0;
        do {
            changed = assignPointsToCentroids(data);
            updateCentroids(data);
            iter++;
        } while (changed && iter < maxIter);
    }

    private void initializeCentroids(double[][] data) {
        for (int i = 0; i < kMeansClusters; i++) {
            int indexN = random.nextInt(data.length);
            centroids[i] = data[indexN];
        }
    }

    private boolean assignPointsToCentroids(double[][] data) {
        boolean changed = false;
        for (int n = 0; n < data.length; n++) {
            double minDistance = Double.MAX_VALUE;
            int minCluster = 0;
            for (int j = 0; j < kMeansClusters; j++) {
                double distance = distance(data[n], j);
                if (distance < minDistance) {
                    minDistance = distance;
                    minCluster = j;
                }
            }
            if (assignments[n] != minCluster) {
                assignments[n] = minCluster;
                changed = true;
            }
        }
        return changed;
    }

    private void updateCentroids(double[][] data) {
        final int[] counts = new int[kMeansClusters];
        final double[][] sum = new double[kMeansClusters][qLen];
        for (int i = 0; i < data.length; i++) {
            int cluster = assignments[i];
            for (int j = 0; j < data[i].length; j++) {
                sum[cluster][j] += data[i][j];
            }
            counts[cluster]++;
        }
        for (int i = 0; i < kMeansClusters; i++) {
            for (int j = 0; j < qLen; j++) {
                if (counts[i] != 0) {
                    centroids[i][j] = sum[i][j] / counts[i];
                }
            }
        }
    }

    public double distance(double[] normed, int centroid) {
        return FastMath.sqrt(lib.euclideanSquaredDistance(centroids[centroid], normed));
    }

    public void serialize(DataOutputStream stream) {
        try {
            for (double[] centroid : centroids) {
                for (double v : centroid) {
                    stream.writeDouble(v);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}