package net.jelter.algorithms.mseg;

import net.jelter.algorithms.multistindex.MinimumBoundingRectangle;
import net.jelter.io.DataManager;
import net.jelter.algorithms.Algorithm;
import net.jelter.utils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static net.jelter.utils.Parameters.*;
import static net.jelter.utils.Parameters.dimensions;

public class MSeg extends Algorithm {
    final JensNode[] jensNodes = new JensNode[N];
    final KMeans[] kMeans = new KMeans[dimensions];

    public void buildIndex() {
        DFTUtils.computeKMeansClusters(kMeans);
        lib.getStream(IntStream.range(0, N).boxed())
                .forEach(n -> {
                    final FourierTrail fourierTrail = DFTUtils.getFourierTrail(n, kMeans);
                    jensNodes[n] = new JensNode(n, 0, fourierTrail.getLength() - 1,
                            fourierTrail.getTrail(), fourierTrail.getLandmarkPortfolios());
                });
    }

    public List<TeunTuple3> kNN(int k, double[][] query) {
        final PriorityBlockingQueue<TeunTuple3> topK = new PriorityBlockingQueue<>(k + 5, TeunTuple3.compareByDistanceReversed());

        final double[] querySumOfSquares = DFTUtils.getSumsOfSquares(query);
        final double[][][] qNorms = DFTUtils.getQNorms(query);

        final Tuple2<double[], LandmarkPortfolio> queryDFTs = DFTUtils.getQueryDFTs(query, kMeans);
        final double[] fftQFlat = queryDFTs._1;
        final LandmarkPortfolio queryPortfolio = queryDFTs._2;
        final MinimumBoundingRectangle queryMBR = new MinimumBoundingRectangle(fftQFlat, fftQFlat, new LandmarkMBR(queryPortfolio));

//        Query the ts-level indices
        lib.getStream(IntStream.range(0, N).boxed())
                .forEach(n -> {
                    if (!DataManager.supportsQuery(n)) {
                        // If one variate is included in the query but not in the time series, continue
                        return;
                    }

                    final long indexSearchStart = System.currentTimeMillis();


                    final int nSubsequences = DataManager.noSubsequences(n);
                    List<CandidateSegment> candidateSegments;
                    if (topK.isEmpty() || topK.size() < k) { // If topK is empty, we should search the entire time series
                        candidateSegments = Collections.singletonList(new CandidateSegment(n, 0, nSubsequences - 1, null));
                    } else {
                        candidateSegments = jensNodes[n].queryBranchRecursive(queryMBR, topK.peek().distance);
                    }
                    if (candidateSegments.isEmpty()) {
                        return;
                    }

                    final int totalCount = candidateSegments.stream().mapToInt(c -> c.getSegmentRange().getLength()).sum();
                    segmentsUnderThreshold.getAndAdd(totalCount);

                    final double threshold = topK.size() == k ? topK.peek().distance : Double.MAX_VALUE;
                    candidateSegments = DFTUtils.optimizeCandidates(candidateSegments, threshold);

                    final long exhaustiveStart = System.currentTimeMillis();
                    indexSearchTime += exhaustiveStart - indexSearchStart;

                    // Perform MASS on the selected segments
                    DFTUtils.updateTopKWithMASS(candidateSegments, qNorms, querySumOfSquares, topK, k);

                    exhaustiveTime += System.currentTimeMillis() - exhaustiveStart;
                });
        final ArrayList<TeunTuple3> result = new ArrayList<>(topK);
        result.sort(TeunTuple3.compareByDistance());
        return result;
    }

    private void writeObject(DataOutputStream stream) throws IOException {
        for (JensNode jensNode : jensNodes) {
            jensNode.serialize(stream);
        }
        if (kMeansClusters != 0) {
            for (KMeans kMean : kMeans) {
                kMean.serialize(stream);
            }
        }
    }

    private void readObject(DataInputStream stream) throws IOException, ClassNotFoundException {
        for (int i = 0; i < N; i++) {
            jensNodes[i] = JensNode.deserialize(stream);
        }
        if (kMeansClusters != 0) {
            for (int i = 0; i < dimensions; i++) {
                kMeans[i] = KMeans.deserialize(stream);
            }
        }
    }


    @Override
    public void saveIndex() {
        String filename = getIndexPath();
        try {
            FileOutputStream fileOut = new FileOutputStream(filename);
            DataOutputStream out = new DataOutputStream(fileOut);
            writeObject(out);
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
            MSeg mseg = new MSeg();
            mseg.readObject(in);
            in.close();
            fileIn.close();
            System.out.println("Deserialized data is read");
            return mseg;
        } catch (IOException i) {
            i.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("MSeg class not found");
            c.printStackTrace();
        }
        return null;
    }
}
