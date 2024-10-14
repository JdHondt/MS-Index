package net.jelter;

import net.jelter.algorithms.AlgorithmType;
import net.jelter.io.DataLoader;
import net.jelter.io.DataManager;
import net.jelter.utils.Parameters;
import net.jelter.utils.lib;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

import static net.jelter.utils.Parameters.*;

public class ComputeCoefficients {
    public static void loadParameters() {
        algorithmType = AlgorithmType.BRUTE_FORCE;
        datasetType = DatasetType.HUGA;
        dataPath = "C:\\Users\\20191810\\OneDrive - TU Eindhoven\\Courses\\Year 5\\Thesis\\datasets";
        normalize = true;
        seed = 0;
        maxM = Integer.MAX_VALUE;
        parallel = false;
        N = 1000;

        switch (datasetType) {
            case HUGA:
                dimensions = 20;
                qLen = 1024;
                break;
            case STOCKS:
                dimensions = 4;
                qLen = 730;
                break;
            case SYNTHETIC:
                dimensions = 6;
                qLen = 1024;
                break;
            case WEATHER:
                dimensions = 4;
                qLen = 1488;
                break;
            default:
                throw new RuntimeException("Unknown dataset type");
        }

        //        Parameter checks
        if (qLen > maxM) throw new RuntimeException("qLen > maxM");

//        Initialize random
        Parameters.newRandom();

//        Build full dataPath and workloadPath
        dataPath += "/" + datasetType.toString().toLowerCase();

        DataManager.data = DataLoader.loadData();
        Parameters.setPrecomputedAngles();
        if (normalize) {
            DataManager.computeMeansStds();
        }
    }

    public static void main(String[] args) {
        loadParameters();

        final DoubleFFT_1D fft = new DoubleFFT_1D(qLen);

        // Pick a query
        final double[][] query = new double[dimensions][];
        final int queryTsId = random.nextInt(N);
        final int querySSId = random.nextInt(DataManager.data[queryTsId][0].length - qLen + 1);
        for (int d = 0; d < dimensions; d++) {
            query[d] = DataManager.getSubSequence(queryTsId, d, querySSId);
            fft.realForward(query[d]);
        }

        double[] percentages = new double[qLen];
        // Compute the FFT of the query

        int nSamples = 1000;

        for (int x = 0; x < nSamples; x++) {
            int tsId = random.nextInt(N);
            int ssId = random.nextInt(DataManager.data[tsId][0].length - qLen + 1);
            for (int d = 0; d < dimensions; d++) {
                final double[] ss = DataManager.getSubSequence(tsId, d, ssId);
                fft.realForward(ss);
                double sum = 0;
                final double[] result = new double[qLen];
                for (int i = 0; i < qLen; i++) {
                    result[i] = lib.pow2(ss[i] - query[d][i]);
                    if (i != 0) {
                        result[i] *= 2;
                    }
                    sum += result[i];
                }
                for (int i = 0; i < qLen; i++) {
                    percentages[i] += result[i] / sum;
                }
            }
        }

        for (int i = 0; i < qLen; i++) {
            percentages[i] /= nSamples * dimensions;
        }

        final double[] percentagesFlat = new double[qLen / 2];
        for (int i = normalize ? 1 : 0; i < qLen / 2; i++) {
            percentagesFlat[i] = percentages[i * 2] + percentages[i * 2 + 1];
        }

        int coeffs = 0;
        for (int i = normalize ? 1 : 0; i < qLen / 2; i++) {
            if (percentages[i * 2] + percentages[i * 2 + 1] > 0.03) {
                coeffs++;
            } else {
                break;
            }
        }
        System.out.println("Coefficients: " + coeffs);
        System.out.println(Arrays.toString(percentagesFlat));
    }
}
