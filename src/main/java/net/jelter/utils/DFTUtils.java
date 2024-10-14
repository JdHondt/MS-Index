package net.jelter.utils;

import net.jelter.algorithms.msindex.segmentation.SegmentMethods;
import net.jelter.algorithms.msindex.segmentation.Segmentation;
import net.jelter.io.DataManager;
import net.jelter.utils.rtreemulti.Entry;
import net.jelter.utils.rtreemulti.geometry.Geometry;
import org.apache.commons.math3.util.FastMath;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static net.jelter.utils.Parameters.*;
import static net.jelter.utils.Parameters.kMeansClusters;
import static net.jelter.utils.lib.reverseAndPadd;

public class DFTUtils {

    // Computes the optimal MASS plan
    public static Tuple2<List<Range>, Double> optimalPlan(List<Range> toInspect, double maxCost) {
        if (toInspect.isEmpty()) {
            return new Tuple2<>(Collections.emptyList(), 0.);
        }

//        Reset any cached optimal plans
        toInspect.forEach(Range::reset);

        final Range first = toInspect.get(0);
        final int lastIndex = toInspect.get(toInspect.size() - 1).getEnd();

        final int nSubsequences = first.getEnd() - first.getStart() + 1;

        Tuple2<List<Range>, Double> result = new Tuple2<>(null, maxCost);
        for (int i = maxMLog2; i >= qLenLog2; i--) {
            final int pow = 1 << i;
            final double thisCost = i * pow;
            if (thisCost >= result._2) {
                continue;
            }

            final int coverage = pow - qLen + 1;
            if (nSubsequences / coverage >= 3) break;

            final int covered = FastMath.min(first.getStart() + coverage, lastIndex); // Includes

            if (covered == lastIndex) {
                result = new Tuple2<>(Collections.singletonList(new Range(first.getStart(), lastIndex)), thisCost);
                continue;
            }

            final ArrayList<Range> remainingToInspect = new ArrayList<>();
            int currentEnd = covered;

            for (final Range left : toInspect) {
                if (left.getEnd() <= covered) {
                    currentEnd = left.getEnd();
                    continue;
                }
                if (left.getStart() > covered) {
                    remainingToInspect.add(left);
                    continue;
                }
                currentEnd = covered;
                remainingToInspect.add(new Range(covered + 1, left.getEnd()));
            }
            final Tuple2<List<Range>, Double> remainingResult;
            if (remainingToInspect.isEmpty() || remainingToInspect.get(0).optimalPlan == null) {
                remainingResult = optimalPlan(remainingToInspect, result._2 - thisCost);
            } else {
                remainingResult = remainingToInspect.get(0).optimalPlan;
            }
            if (remainingResult._1 == null) {
                continue;
            }

            if (remainingResult._2 + thisCost < result._2) {
                final List<Range> newResult;
                if (remainingResult._1.isEmpty()) {
                    newResult = Collections.singletonList(new Range(first.getStart(), currentEnd));
                } else {
                    newResult = new ArrayList<>(remainingResult._1.size() + 1);
                    newResult.add(new Range(first.getStart(), currentEnd));
                    newResult.addAll(remainingResult._1);
                }
                result = new Tuple2<>(newResult, remainingResult._2 + thisCost);
            }
        }
        toInspect.get(0).optimalPlan = result;
        return result;
    }

    //    Computes the fft of each dimension of a time series
    public static double[] fft(double[] timeSeries) {
        final int m = timeSeries.length;

        final int nextPowTwo = lib.nextPowerOfTwo(m);
        final DoubleFFT_1D FFT = getPrecomputedFFT(nextPowTwo);

//        Copy the time series to a new array, padded to nextPowTwo, and perform FFT on each dimension
        final double[] fftTimeSeries = new double[nextPowTwo];
        System.arraycopy(timeSeries, 0, fftTimeSeries, 0, m);
        FFT.realForward(fftTimeSeries);

        return fftTimeSeries;
    }

    public static FourierTrail getFourierTrail(int n, KMeans[] kMeans) {
        final int noSubsequences = DataManager.noSubsequences(n);
        double[][] trail = new double[noSubsequences][];
        final LandmarkPortfolio[] landmarkPortfolios = kMeansClusters == 0 | kMeans == null ? null : new LandmarkPortfolio[noSubsequences];

        final int nCoeffsPerDimension = fourierLengthPerDimension();
        final int fourierLength = fourierLength();

        //        Get the fourier trajectory of the subsequences of the time series
        for (int i = 0; i < trail.length; i++) {
//            Compute the first f DFT-coefficients of the subsequence.
            trail[i] = new double[fourierLength];

            double[][] landmarkDistances = landmarkPortfolios != null ? new double[dimensions][kMeansClusters] : null;

            for (int d = 0; d < dimensions; d++) {
                final double[] subSequence = DataManager.getSubSequence(n, d, i);
                final double[] coefficients = DFTUtils.computeCoefficients(subSequence);
                System.arraycopy(coefficients, 0, trail[i], d * nCoeffsPerDimension, nCoeffsPerDimension);

                if (landmarkPortfolios != null) {
                    final double[] remainder = DFTUtils.undoFFT(subSequence, coefficients);
                    for (int k = 0; k < kMeansClusters; k++) {
                        landmarkDistances[d][k] = kMeans[d].distance(remainder, k);
                    }
                }
            }

            if (landmarkPortfolios != null) {
                landmarkPortfolios[i] = new LandmarkPortfolio(landmarkDistances);
            }
        }

        return new FourierTrail(trail, landmarkPortfolios);
    }

    public static List<Entry<CandidateSegment, Geometry>> getSegmentedFourierTrail(KMeans[] kMeans){
        return lib.getStream(IntStream.range(0, N).boxed())
                .flatMap(i -> {
                    FourierTrail fourierTrail = DFTUtils.getFourierTrail(i, kMeans);
                    List<Entry<CandidateSegment, Geometry>> segments = Segmentation.get(segmentMethod).segment(i, fourierTrail);

//                    Update stats
                    if (segmentMethod != SegmentMethods.ADHOC) {
                        nSegments.getAndAdd(segments.size());
                    }

                    return segments.stream();
                })
                .collect(Collectors.toList());
    }

    // Processes the ranges to filter based on the currently derived kThDistance and prepares
    // the ranges for the optimal MASS computation.
    public static List<CandidateSegment> optimizeCandidates(List<CandidateSegment> candidateSegments, double kThDistance) {
        // Filter based on the k-th distance
        if (Double.isFinite(kThDistance)) {
            candidateSegments = candidateSegments.stream().filter(c -> c.getMbr() == null || c.getCurrLB() <= kThDistance).collect(toList());
        }

        if (candidateSegments.isEmpty()) {
            return candidateSegments;
        }

//        Make sure all the segments are from the same time series
        int tsIdx = candidateSegments.get(0).getTimeSeriesIndex();
        if (candidateSegments.stream().anyMatch(segment -> segment.getTimeSeriesIndex() != tsIdx)) {
            throw new IllegalStateException("All segments should be from the same time series before merging");
        }

        // Merge neighboring ranges
        final ArrayList<CandidateSegment> mergedSegments = CandidateSegment.mergeCandidateSegments(candidateSegments, false);

        // Compute optimal MASS plan
        List<Range> ranges = mergedSegments.stream().map(CandidateSegment::getSegmentRange).collect(toList());


        final int totalCount = ranges.stream().mapToInt(Range::getLength).sum();
        final int nSubsequences = DataManager.noSubsequences(tsIdx);

//        Compute optimal MASS plan
        if (computeOptimalPlan && !(qLen < 512 && (candidateSegments.size() > 30 || totalCount * 1.5 >= nSubsequences))) {
            ranges = DFTUtils.optimalPlan(ranges, Double.POSITIVE_INFINITY)._1;
        } else { // optimalPlan might take too long to compute
//            Merge all the ranges
            ranges = Collections.singletonList(new Range(ranges.get(0).getStart(), ranges.get(ranges.size() - 1).getEnd()));
        }

        return ranges.stream().map(range -> new CandidateSegment(tsIdx, range.getStart(), range.getEnd(), null)).collect(toList());
    }



    /**
     * Multiplies two arrays of doubles element-wise.
     *
     * @param a Array of doubles.
     * @param b Array of doubles.
     * @return Element-wise product of the two arrays.
     */
    private static double[] multiplyElementwise(double[] a, double[] b) {
        double[] out = new double[a.length];
        out[0] = a[0] * b[0];
        out[1] = a[1] * b[1];

        for (int i = 2; i < a.length; i += 2) {
            double aRe = a[i];
            double aIm = a[i + 1];
            double bRe = b[i];
            double bIm = b[i + 1];
            out[i] = aRe * bRe - aIm * bIm;
            out[i + 1] = aRe * bIm + aIm * bRe;
        }
        return out;
    }

    public static double[] MASS(CandidateSegment candidateSegment, double[][][] qNorms, double[] querySumOfSquares) {
//        Unpack candidate segment
        final int timeSeriesIndex = candidateSegment.getTimeSeriesIndex();
        Range segmentRange = candidateSegment.getSegmentRange();
        final int rangeLength = segmentRange.getLength();

//        Get necessary cached values for FFT
        final int power2Length = segmentRange.getPower2Length();
        final DoubleFFT_1D FFT = getPrecomputedFFT(power2Length);

        final int qNormIndex = Integer.numberOfTrailingZeros(segmentRange.getPower2Length()) - qLenLog2;

        final double[] distances = new double[rangeLength];
        for (int d : selectedVariatesIdx) {
            final double[] qNorm = qNorms[d][qNormIndex];

//                FFT the segment
            final double[] timeseriesFFT = DataManager.getPartialSeriesForMASS(timeSeriesIndex, d, segmentRange);
            FFT.realForward(timeseriesFFT);

//            Compute the distances
            final double[] dimDistances = MASS(candidateSegment, timeseriesFFT, qNorm, querySumOfSquares, d);
            for (int i = 0; i < rangeLength; i++) {
                distances[i] += dimDistances[i];
            }
        }

        subsequencesExhChecked.getAndAdd(rangeLength);
        return distances;
    }

    /**
     * Compute the distances between the query and the subsequences of the time series where we already have the fft of the time series
     * @param candidateSegment: The candidate segment
     * @param timeseriesFFT: Precomputed FFT of the time series
     * @param qNorm: The FFT of the query
     * @param querySumOfSquares: The sum of squares of the query
     * @return
     */
    public static double[] MASS(CandidateSegment candidateSegment, double[] timeseriesFFT, double[] qNorm, double[] querySumOfSquares, int dimension) {
        // FFT(Q) * FFT(T)
        final double[] multiplied = multiplyElementwise(timeseriesFFT, qNorm);

        // IFFT
        getPrecomputedFFT(timeseriesFFT.length).realInverse(multiplied, true);

        final Range range = candidateSegment.getSegmentRange();
        final int rangeLength = range.getLength();

        final double[] distances = new double[rangeLength];

        //                Post-process the distances
        for (int i = 0; i < distances.length; i++) { // Loop over all possible time series
            final double dot = multiplied[i + qLen - 1]; // Get the real part of the dot product

            if (normalize) {
                final double std = DataManager.getStd(candidateSegment.getTimeSeriesIndex(), dimension, range.getStart() + i);
                if (std == 0) distances[i] = qLen;
                else distances[i] = 2 * (qLen - dot / std);
            } else {
                final double squaredSum = DataManager.getSquaredSum(candidateSegment.getTimeSeriesIndex(), dimension, range.getStart() + i);
                distances[i] = squaredSum - 2 * dot + querySumOfSquares[dimension];
            }
        }

        return distances;
    }



    public static void updateTopKWithMASS(List<CandidateSegment> candidateSegments, double[][][] qNorms, double[] querySumOfSquares,
                                          PriorityBlockingQueue<MSTuple3> topK, int k){
        for (CandidateSegment segment: candidateSegments){
            final double[] distances = DFTUtils.MASS(segment, qNorms, querySumOfSquares);
            final int start = segment.getStart();

//                Update topk
            for (int i = 0; i < distances.length; i++) {
                final double distance = distances[i];
                if (topK.size() < k || distance < topK.peek().distance()) {
                    topK.add(new MSTuple3(distance, segment.getTimeSeriesIndex(), start + i));
                    if (topK.size() > k) {
                        topK.poll();
                    }
                }
            }
        }
    }

    // Compute the DFT of the first nCoeffs coefficients of the query
    // Compute the distances to the landmarks based on the remaining coefficients
    public static Tuple2<double[], LandmarkPortfolio> getQueryDFTs(double[][] query, KMeans[] kMeans) {
        final double[] fftQFlat = new double[fourierLength()];
        final double[][] landmarkDistances = new double[dimensions][kMeansClusters];

        final int nCoeffsPerDimension = fourierLengthPerDimension();

//        Compute distances to landmarks
        for (int d : selectedVariatesIdx) {
            final double[] coefficients = computeCoefficients(query[d]);
            System.arraycopy(coefficients, 0, fftQFlat, d * nCoeffsPerDimension, nCoeffsPerDimension);
            if (kMeansClusters != 0) {
                // Get distance
                double[] remainder = undoFFT(query[d], coefficients);
                for (int i = 0; i < kMeansClusters; i++) {
                    landmarkDistances[d][i] = kMeans[d].distance(remainder, i);
                }
            }
        }
        return new Tuple2<>(fftQFlat, kMeansClusters == 0 ? null: new LandmarkPortfolio(landmarkDistances));
    }

    // Computes the first nCoeffs DFT coefficients of the input and stores the coefficients in multiDimSubSequence at the given offset
    public static double[] computeCoefficients(double[] input) {
        final int n = input.length;
        final int nCoeffs = fftCoefficients();

        final double[] output = new double[nCoeffs * 2];

        for (int i = 0; i < nCoeffs; i++) {
            double realPart = 0;
            double imagPart = 0;

            for (int t = 0; t < n; t++) {
                realPart += input[t] * precomputedAnglesCos[i][t];
                imagPart -= input[t] * precomputedAnglesSin[i][t];
            }

            output[i*2] = realPart;
            output[i*2 + 1] = imagPart;
        }
        return output;
    }

    // Removes the waves of the coefficients from the subsequence
    public static double[] undoFFT(double[] ss, double[] dfts) {
        int nCoeffs = dfts.length / 2;
        double[] out = ss.clone();

        for (int i = 0; i < nCoeffs; i++) {
            int realId = i * 2;
            int imagId = realId + 1;

            // Convert the values to the time domain
            double real = dfts[realId] / qLen;
            double imag = dfts[imagId] / qLen;

            // Since we take symmetry into consideration, we should subtract these waves twice
            if (fftCoefficients[i] != 0) {
                real *= 2;
                imag *= 2;
            }

            final double[] cossie = precomputedAnglesCos[i];
            final double[] sinnie = precomputedAnglesSin[i];

            for (int n = 0; n < out.length; n++) {
                out[n] -= real * cossie[n] - imag * sinnie[n]; // Subtract the waves of coefficient k from the subsequence
            }
        }
        return out;
    }

    // Computes the landmarks
    public static void computeKMeansClusters(KMeans[] kMeans) {
        if (kMeansClusters != 0) {
            final int perTS = 5;
            Parameters.newRandom();
            for (int d = 0; d < dimensions; d++) {
                kMeans[d] = new KMeans(100);
                final ArrayList<double[]> randomSubsample = new ArrayList<>();
                for (int n = 0; n < N; n++) {
                    if (!DataManager.supportsVariate(n, d)) continue;
                    for (int i = 0; i < perTS; i++) {
                        final int randomIndex = random.nextInt(DataManager.noSubsequences(n));
                        final double[] randomSubSequence = DataManager.getSubSequence(n, d, randomIndex);
                        final double[] dft = computeCoefficients(randomSubSequence);
                        final double[] remainder = undoFFT(randomSubSequence, dft);
                        randomSubsample.add(remainder);
                    }
                }

                kMeans[d].run(randomSubsample.toArray(new double[0][]));
            }
        }
    }

    // Get the padded and reversed FFT of the query, for different powers of 2 in length (i.e., [qLen, 2*qLen, 4*qLen, ...])
    public static double[][][] getQNorms(double[][] query) {
        final int length = lib.log2(lib.nextPowerOfTwo(maxM)) - qLenLog2 + 1;
        double[][][] qNorms = new double[dimensions][length][];
        int currentCount = lib.nextPowerOfTwo(qLen);

//        Only get the qNorms for the selected variates
        for (int i = 0; i < length; i++) {
            for (int d : selectedVariatesIdx) {
                qNorms[d][i] = reverseAndPadd(query[d], currentCount);
                getPrecomputedFFT(currentCount).realForward(qNorms[d][i]);
            }
            currentCount *= 2;
        }
        return qNorms;
    }

    // Get the sum of squares of the query. This is used in the MASS computation
    public static double[] getSumsOfSquares(double[][] query) {
        final double[] querySumOfSquares;
        if (normalize) {
            querySumOfSquares = new double[0];
        } else {
            querySumOfSquares = new double[query.length];

//            Only compute the sum of squares for the selected variates
            for (int d : selectedVariatesIdx) {
                for (double v : query[d]) {
                    querySumOfSquares[d] += v * v;
                }
            }
        }
        return querySumOfSquares;
    }
}
