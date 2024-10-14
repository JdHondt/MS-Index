package io.github.algorithms.mass;

import io.github.io.DataManager;
import io.github.algorithms.Algorithm;
import io.github.utils.CandidateSegment;
import io.github.utils.DFTUtils;
import io.github.utils.MSTuple3;
import io.github.utils.lib;

import java.io.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.*;
import static io.github.utils.Parameters.nSubsequences;

public class MVMASS extends Algorithm {
    double[][][] timeseriesFFTs; // shape (N, dimensions, coefficients)

    public List<MSTuple3> kNN(int k, double[][] query) {
        subsequencesExhChecked.getAndAdd(nSubsequences);
        segmentsUnderThreshold.getAndAdd(N);

        final double[] querySumOfSquares = DFTUtils.getSumsOfSquares(query);
        final double[][][] qNorms = DFTUtils.getQNorms(query);
        final PriorityBlockingQueue<MSTuple3> topK = new PriorityBlockingQueue<>(k, MSTuple3.compareByDistanceReversed());

        lib.getStream(IntStream.range(0, N).boxed()).forEach(n -> {
            if (!DataManager.supportsQuery(n)) {
                // If one variate is included in the query but not in the time series, continue
                return;
            }

//            Get the query FFT
            final int qNormIndex = Integer.numberOfTrailingZeros(lib.nextPowerOfTwo(DataManager.getM(n))) - qLenLog2;

//            Create a candidate segment
            final int nSubsequences = DataManager.noSubsequences(n);
            final CandidateSegment candidateSegment = new CandidateSegment(n, 0, nSubsequences - 1, null);

            final double[] distances = new double[nSubsequences];
            for (int d : selectedVariatesIdx) {
                final double[] qNorm = qNorms[d][qNormIndex];
                final double[] dimDistances = DFTUtils.MASS(candidateSegment, timeseriesFFTs[n][d], qNorm, querySumOfSquares, d);

//                Add to the total distance
                for (int i = 0; i < dimDistances.length; i++) {
                    distances[i] += dimDistances[i];
                }
            }

//                Iteratively add to topk
            for (int j = 0; j < distances.length; j++) {
                if (topK.size() < k) {
                    topK.add(new MSTuple3(distances[j], n, j));
                } else if (distances[j] < topK.peek().distance()) {
                    topK.add(new MSTuple3(distances[j], n, j));
                    topK.poll();
                }
            }
        });

        return topK.stream().sorted(MSTuple3.compareByDistance()).collect(Collectors.toList());
    }


//    Precompute the FFT for each timeseries in the dataset
    public void buildIndex() {
        final double[][][] data = DataManager.data;
        timeseriesFFTs = new double[data.length][channels][]; // shape (N, dimensions, coefficients)
        for (int i = 0; i < data.length; i++) {
            for (int d = 0; d < channels; d++) {
                if (DataManager.supportsVariate(i, d)) {
                    timeseriesFFTs[i][d] = DFTUtils.fft(data[i][d]);
                }
            }
        }
    }

    public void serialize(DataOutputStream out) {
        for (double[][] timeseriesFFT : timeseriesFFTs) {
            for (double[] dimFFT : timeseriesFFT) {
                for (double val : dimFFT) {
                    try {
                        out.writeDouble(val);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private void readObject(DataInputStream in) {
        for (int i = 0; i < timeseriesFFTs.length; i++) {
            for (int d = 0; d < timeseriesFFTs[i].length; d++) {
                for (int j = 0; j < timeseriesFFTs[i][d].length; j++) {
                    try {
                        timeseriesFFTs[i][d][j] = in.readDouble();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void saveIndex() {
        String filename = getIndexPath();
        try {
            FileOutputStream fileOut = new FileOutputStream(filename);
            DataOutputStream out = new DataOutputStream(fileOut);
            serialize(out);
            out.close();
            fileOut.close();
            System.out.println("Serialized data is saved");
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    @Override
    public Algorithm loadIndex() {
        String filename = getIndexPath();
        try {
            FileInputStream fileIn = new FileInputStream(filename);
            DataInputStream in = new DataInputStream(fileIn);
            MVMASS mass = new MVMASS();
            mass.readObject(in);
            in.close();
            fileIn.close();
            System.out.println("Deserialized data is read");
            return mass;
        } catch (IOException i) {
            i.printStackTrace();
        }
        return null;
    }


}
