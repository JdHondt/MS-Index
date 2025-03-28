package io.github.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.algorithms.AlgorithmType;
import io.github.algorithms.msindex.DimensionWeightingStrategy;
import io.github.algorithms.msindex.segmentation.SegmentMethods;
import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.logging.*;
import java.util.stream.IntStream;

import static java.lang.System.exit;

public class Parameters {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Hide {
    } // Make sure it's not saved as a statistic

    public static AlgorithmType algorithmType = AlgorithmType.MASS; // Index algorithmType
    public static String dataPath = "/home/data";
    public static String datasetName;
    public static int N = 100; // # Time series = min(#dataset, maxN)
    public static int maxM = (int) 1e6; // # Maximum length of Time series
    public static int qLen = 100; // Length of query
    public static int K = 10; // top k
    public static boolean normalize = false; // z-normalize the dataset and query (currently doesn't work when false)
    public static int nQueries = 10; // # Queries
    public static RuntimeMode runtimeMode = RuntimeMode.FULL_NO_STORE; // Index, Query (and possibly also store index), QueryNoStore (doesn't store index)
    public static boolean computeOptimalPlan = true; // For MS index
    public static int kMeansClusters = 1;
    public static double queryNoiseEps = .1; // For both query and synthetic dataset
    public static double indexLeafSizePercentage = 0.0005; // For the trees, capacity per node in percentage of subsequences
    public static int indexLeafSize; // Derived; absolute amount of subsequences per node
    public static int experimentId = 0; // experiment id
    public static int seed = 0; // Random seed
    public static double datasetSize;
    public static int nQueryChannels;
    public static int[] selectedVariatesIdx;
    public static DimensionWeightingStrategy dimensionWeightingStrategy = DimensionWeightingStrategy.VARIANCE;
    public static boolean queryFromIndexed = true;
    public static int kvMatchSegmentDivisor = 1;

    @Hide
    public static Random random;
    public static boolean parallel = false; // Use parallelism

    //    FFT-related
    public static String fftConfigPath = "resources"; // The path to the json file containing the covered distance by each coefficient and each channel
    public static double fftCoveredDistance = -1; // The desired distance covered by the FFT coefficients
    public static int[] fftCoefficients; // DERIVED - the idx of the coefficients to use in fourier transform
    @Hide public static double[] coeffWeights; // DERIVED - shape=(dimensions * nCoefficients * 2) - the covered distance (in percentage) by each coefficient for each dimension

    //    Specifically for MST
    public static SegmentMethods segmentMethod = SegmentMethods.ADHOC;
    public static double segmentParameter = 0;

    //    Set later
    @Hide public static ArrayList<Path> variatePaths = new ArrayList<>();
    public static String[] selectedVariatesNames;
    public static int channels; // # Dimensions
    public static long nSubsequences;
    @Hide
    static DoubleFFT_1D[] precomputedFFTs;
    @Hide
    public static int qLenLog2;
    @Hide
    public static int maxMLog2;
    @Hide
    public static double[][] precomputedAnglesCos;
    @Hide
    public static double[][] precomputedAnglesSin;

    public static Long memoryUsageBeforeIndexing;
    public static Long memoryUsageAfterIndexing;


    //    Run Statistics (about the run)
    public static AtomicLong subsequencesExhChecked = new AtomicLong(0);
    public static AtomicLong segmentsUnderThreshold = new AtomicLong(0);
    public static AtomicLong nBoundComputations = new AtomicLong(0);
    public static long nNodes;
    public static long nLeafs;
    public static long nNodesVisited;
    public static long nLeafsVisited;
    public static long indexBuildTime;
    public static long queryTime;
    public static long setUpTime;
    public static long indexSearchTime;
    public static long exhaustiveTime;
    public static double indexSize;
    public static long[] queryTimePerQuery;
    public static AtomicLong nSegments = new AtomicLong(0);
    public static double totalLeafVolume;
    public static double totalLeafLogVolume;
    public static double totalLeafMargin;
    @Hide
    public static int currQueryId = 0;

    //    Defaults
    public static boolean printResults = true; // Print results to console
    public static String outputPath = "output"; // Output path for results
    public static boolean indexing = true;

    public static void setDependentParametersPreLoad() {
        newRandom();

//        Set dataset name to basename of datapath
        datasetName = new File(dataPath).getName();
    }

    public static void setDependentParametersPostLoad() {

        if (qLen == -1) qLen = (int) Math.ceil(.2 * maxM);
        qLen = Math.max(qLen, Math.min(20, maxM)); // at least 20, if not possible, maxM

//        Selected variates config
        setSelectedVariates();


//        FFT config (only for fft-based algorithms
        if (Arrays.asList(AlgorithmType.ST_INDEX, AlgorithmType.MSINDEX).contains(algorithmType)) {
            setFFTConfig(fftConfigPath);
        }

//        Tree config
        if (indexLeafSize == 0){
            setLeafSize(nSubsequences, indexLeafSizePercentage);
        }

        if (!algorithmType.equals(AlgorithmType.BRUTE_FORCE)) {
            setPrecomputedFFTs();
        }
        if (Arrays.asList(AlgorithmType.ST_INDEX,AlgorithmType.MSINDEX).contains(algorithmType)) {
            setPrecomputedAngles();
        }
    }

    private static void setSelectedVariates(){
        if (nQueryChannels == -1 || nQueryChannels > channels){ nQueryChannels = channels; }
        if (nQueryChannels == 0){ nQueryChannels = 1; }

        selectedVariatesIdx = IntStream.range(0, nQueryChannels).toArray();

//        Infer selected variates names
        selectedVariatesNames = Arrays.stream(selectedVariatesIdx).mapToObj(i -> variatePaths.get(i).getFileName().toString().split("\\.")[0]).toArray(String[]::new);
    }

    public static int fftCoefficients(){
        return fftCoefficients.length;
    }

    public static int fourierLengthPerDimension(){return fftCoefficients() * 2;}
    public static int fourierLength(){return fourierLengthPerDimension() * channels;}

    public static void setPrecomputedFFTs() {
        qLenLog2 = lib.log2(lib.nextPowerOfTwo(qLen));
        maxMLog2 = lib.log2(lib.nextPowerOfTwo(maxM));
        precomputedFFTs = new DoubleFFT_1D[maxMLog2 - qLenLog2 + 1];
        int currentCount = lib.nextPowerOfTwo(qLen);
        for (int i = 0; i < precomputedFFTs.length; i++) {
            precomputedFFTs[i] = new DoubleFFT_1D(currentCount);
            currentCount *= 2;
        }
    }

    public static void setPrecomputedAngles() {
        precomputedAnglesCos = new double[fftCoefficients()][qLen];
        precomputedAnglesSin = new double[fftCoefficients()][qLen];
        for (int f = 0; f < precomputedAnglesCos.length; f++) {
            int coeffIdx = fftCoefficients[f];
            for (int i = 0; i < qLen; i++) {
                double angle = 2 * Math.PI * i * coeffIdx / qLen;
                precomputedAnglesCos[f][i] = Math.cos(angle);
                precomputedAnglesSin[f][i] = Math.sin(angle);
            }
        }
    }

    public static DoubleFFT_1D getPrecomputedFFT(int n) {
        final int index = Integer.numberOfTrailingZeros(n) - qLenLog2;
        return precomputedFFTs[index];
    }

    /**
     * Method that takes in the config file containing the covered distance by each coefficient and each channel,
     * and derives what coefficients to use in the fourier transform based on the desired distance to cover.
     */
    private static void setFFTConfig(String configPath) {
        Logger.getGlobal().info("Setting FFT config");

        if (fftCoveredDistance > 1){
            Logger.getGlobal().severe("FFT covered distance should be a percentage, not a value greater than 1");
            exit(1);
        }

//        Read the config file (json)
        ObjectMapper mapper = new ObjectMapper();
        String path = normalize ? configPath + "/diffs_norm.json" : configPath + "/diffs.json";
        double[][] coveredDistances = new double[selectedVariatesNames.length][100];
        double[] avgCoveredDistances = new double[100];
        JsonNode fftConfig = null;
        try {
            fftConfig = mapper.readTree(new File(path));

            String datasetNameStr = datasetName.toLowerCase().replace("_", "-");

//            Get the covered distances for each of the selected variates
            for (int i = 0; i < selectedVariatesNames.length; i++) {
                ArrayNode arrayNode = (ArrayNode) fftConfig.get(datasetNameStr + "_" + selectedVariatesNames[i]);
                if (arrayNode != null) {
                    for (int j = 0; j < arrayNode.size(); j++) {
                        coveredDistances[i][j] = arrayNode.get(j).asDouble();
                        avgCoveredDistances[j] += coveredDistances[i][j] / selectedVariatesNames.length;
                    }
                } else { // Assume all distance is in first coefficient
                    int coefId = normalize ? 1: 0;
                    coveredDistances[i][coefId] = 1;
                    avgCoveredDistances[coefId] += 1d / selectedVariatesNames.length;
                }
            }
        } catch (IOException e) {
            Logger.getGlobal().severe("Could not read the FFT config file, reverting to default coefficients");
            fftCoveredDistance = -1;
        }

//            Get the coefficients to use
        if (fftCoveredDistance < 0){
            fftCoefficients = normalize ? new int[]{1,2}: new int[]{0,1};
        }

        if (fftCoefficients == null) {
            int[] sortedCoefficients = lib.argsort(avgCoveredDistances, false);
            double runningCovDist = 0;
            ArrayList<Integer> tmpCoefficients = new ArrayList<>(coveredDistances.length);
            for (int i = 0; i < sortedCoefficients.length; i++) {
                int idx = sortedCoefficients[i];
                tmpCoefficients.add(idx);
                runningCovDist += avgCoveredDistances[idx];
                if (i == 19 || runningCovDist > fftCoveredDistance) break; // max of 20 coefficients
            }
            fftCoefficients = tmpCoefficients.stream().mapToInt(i -> i).toArray();

        }
        Logger.getGlobal().info("Selected coefficients: " + Arrays.toString(fftCoefficients));

//            Derive the weighting for each of the coefficients (including imaginary part)
        coeffWeights = new double[selectedVariatesIdx.length * fftCoefficients.length];
        double sum = 0;
        for (int i = 0; i < selectedVariatesIdx.length; i++) { // Get covered distances
            for (int j = 0; j < fftCoefficients.length; j++) {
                int idx = fftCoefficients[j];
                double dist = coveredDistances[i][idx];
                coeffWeights[i * fftCoefficients.length + j] = dist;
                sum += dist;
            }
        }
//            Normalize to get the weights
        for (int i = 0; i < coeffWeights.length; i++) {
            coeffWeights[i] /= sum;
        }

    }

    public static void setLeafSize(long nSubsequences, double percentage) {
        if (algorithmType.equals(AlgorithmType.MSINDEX)){
            indexLeafSize = percentage == 0 ? 4: (int) Math.round(nSubsequences * percentage);
        } else {
            switch (algorithmType) {
                case DSTREE:
                    indexLeafSize = 2 + (int) Math.ceil(nSubsequences / 10.0);
                    break;
                case ST_INDEX:
                    indexLeafSize = 8;
                    break;
                default:
                    break;
            }
        }

        System.out.println("Setting leaf size to " + indexLeafSize);
    }

    public static void newRandom() {
        random = new Random(seed);
    }

    public enum RuntimeMode {
        FULL, // Build index, store, and query
        FULL_NO_STORE, // Build index and query
        INDEX, // Build index and store
        INDEX_NO_STORE, // Build index
        QUERY, // Query only
        CHECK_INDEX, // Only measure size of stored index
        CORRECTNESS // Compare results to Brute Force
    }



    //    Return all parameters as a string with \n between them
    public static String printParams() {
        return String.join("\n", Arrays.stream(Parameters.class.getDeclaredFields()).map(f -> {
//            Check if the field is static
            if (Modifier.isStatic((f.getModifiers()))) {
                f.setAccessible(true);

//                Get the value
                try {
                    Object value = f.get(null);
                    return f.getName() + ": " + value;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return "";
            }
        }).toArray(String[]::new));
    }

    //    Append the all parameters to the output file. If the file doesn't exist, create it
    public static void saveParams() {
        try {
            (new File(outputPath)).mkdirs();

            String filePath = String.format("%s/runs.csv", outputPath);
            File file = new File(filePath);

            boolean exists = file.exists();

            FileWriter resultWriter = new FileWriter(file, exists);

            HashMap<String, Object> fieldMap = new HashMap<>();
            Arrays.stream(Parameters.class.getDeclaredFields()).forEach(f -> {
//                Skip hidden fields
                if (f.isAnnotationPresent(Hide.class)) return;

//                Skip switch tables
                if (f.getName().startsWith("$SWITCHTABLE$")) return;

//            Check if the field is static
                if (Modifier.isStatic((f.getModifiers()))) {
                    f.setAccessible(true);

//                Get the value
                    try {
                        Object value = f.get(null);
                        fieldMap.put(f.getName(), value);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return;
                }
            });


//            Create header if necessary


            if (!exists) {
                String header = fieldMap.keySet().stream().collect(Collectors.joining(","));
                resultWriter.write(header + "\n");
            }

//            Write values
            String row = fieldMap.values().stream()
                    .map(o -> {
                        if (o == null) return "";
//                        o.getClass().isArray() ? Arrays.stream((Object[]) o).map(Object::toString).collect(Collectors.joining(";")) : o.toString()
                        if (o.getClass().isArray()) {
//                           Cast to primitive array
                            if (o instanceof double[])
                                return Arrays.stream((double[]) o).mapToObj(Double::toString).collect(Collectors.joining(";"));
                            else if (o instanceof long[])
                                return Arrays.stream((long[]) o).mapToObj(Long::toString).collect(Collectors.joining(";"));
                            else if (o instanceof int[])
                                return Arrays.stream((int[]) o).mapToObj(Long::toString).collect(Collectors.joining(";"));
                            else
                                return Arrays.stream((Object[]) o).map(Object::toString).collect(Collectors.joining(";"));
                        } else {
                            return o.toString();
                        }
                    }).collect(Collectors.joining(","));
            resultWriter.write(row + "\n");
            resultWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred whilst writing the results");
            e.printStackTrace();
            exit(1);
        }

    }

}
