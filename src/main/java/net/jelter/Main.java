package net.jelter;

import net.jelter.algorithms.Algorithm;
import net.jelter.algorithms.AlgorithmType;
import net.jelter.algorithms.BruteForce;
import net.jelter.algorithms.dstree.MVDSTree;
import net.jelter.algorithms.dstree_org.MVDSTreeOrg;
import net.jelter.algorithms.multistindex.MultiSTIndex;
import net.jelter.algorithms.multistindex.MissingValueStrategy;
import net.jelter.algorithms.multistindex.segmentation.SegmentMethods;
import net.jelter.io.DataLoader;
import net.jelter.io.DataManager;
import net.jelter.io.WorkloadGenerator;
import net.jelter.algorithms.mass.MVMASS;
import net.jelter.algorithms.stindex.MultivariateSTIndex;
import net.jelter.algorithms.mseg.MSeg;
import net.jelter.utils.*;

import java.util.*;
import java.util.logging.*;

import static java.lang.System.exit;
import static net.jelter.io.DataManager.selectedVariatesSet;
import static net.jelter.utils.Parameters.*;

public class Main {

    private static void configLogger(Level logLevel) {
        Logger mainLogger = Logger.getGlobal();
        mainLogger.setUseParentHandlers(false);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord lr) {
                String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
                return String.format(format,
                        new Date(lr.getMillis()),
                        lr.getLevel().getLocalizedName(),
                        lr.getMessage()
                );
            }
        });
        handler.setLevel(logLevel);
        mainLogger.addHandler(handler);
        mainLogger.setLevel(logLevel);
    }

    public static void setParameters(String[] args) {
        if (args.length > 0) {
            int i = 0;
            algorithmType = AlgorithmType.valueOf(args[i]);
            i++;
            datasetType = DatasetType.valueOf(args[i].toUpperCase());
            i++;
            dataPath = args[i];
            i++;
            outputPath = args[i];
            i++;
            fftConfigPath = args[i];
            i++;
            queryFromDataset = Boolean.parseBoolean(args[i]);
            i++;
            final int parsedMaxN = Integer.parseInt(args[i]);
            N = parsedMaxN == -1 ? Integer.MAX_VALUE : parsedMaxN;
            i++;
            maxM = Integer.parseInt(args[i]);
            i++;
            final int parsedQD = Integer.parseInt(args[i]);
            dimensions = parsedQD == -1 ? Integer.MAX_VALUE : parsedQD;
            i++;
            qLen = Integer.parseInt(args[i]);
            i++;
            K = Integer.parseInt(args[i]);
            i++;
            normalize = Boolean.parseBoolean(args[i]);
            i++;
            nQueries = Integer.parseInt(args[i]);
            i++;
            runtimeMode = RuntimeMode.valueOf(args[i]);
            i++;
            fftCoveredDistance = Double.parseDouble(args[i]);
            i++;
            queryNoiseEps = Double.parseDouble(args[i]);
            i++;
            indexLeafSizePercentage = Double.parseDouble(args[i]);
            i++;
            experimentId = Integer.parseInt(args[i]);
            i++;
            seed = Integer.parseInt(args[i]);
            i++;
            parallel = Boolean.parseBoolean(args[i]);
            i++;
            selectedVariates = Integer.parseInt(args[i]);
            i++;
            kMeansClusters = Integer.parseInt(args[i]);
            i++;
            computeOptimalPlan = Boolean.parseBoolean(args[i]);
            i++;
            percentageVariatesUsed = Double.parseDouble(args[i]);
            i++;
            mstSegmentMethod = SegmentMethods.valueOf(args[i]);
            i++;
            mstSegmentParameter = Integer.parseInt(args[i]);
            i++;
            missingValueStrategy = MissingValueStrategy.valueOf(args[i]);
        } else {
            Logger.getGlobal().info("Using default parameters");
            algorithmType = AlgorithmType.ST_INDEX;
            datasetType = DatasetType.STOCKS;
            dataPath = "/home/jens/tue/data/MTS/subsequence_search/preprocessed";
            outputPath = "/home/jens/ownCloud/Documents/3.Werk/0.TUe_Research/3.Other_Projects/0.MultivariateTimeSeries/subsequence_search/MTS-subsequence-search/Code/output";
            fftConfigPath = "/home/jens/ownCloud/Documents/3.Werk/0.TUe_Research/3.Other_Projects/0.MultivariateTimeSeries/subsequence_search/MTS-subsequence-search/analysis/dfts";
            queryFromDataset = true;
            N = 100;
            dimensions = -1;
            qLen = 730;
            maxM = 32000;
            K = 1;
            normalize = true;
            nQueries = 10;
            runtimeMode = RuntimeMode.INDEX_NO_STORE;
            fftCoveredDistance = -1;
            queryNoiseEps = 0.1;
            indexLeafSizePercentage = 0.0005;
//            indexLeafSizePercentage = 0;
            experimentId = 0;
            seed = 0;
            parallel = false;
            kMeansClusters = 1;
            computeOptimalPlan = true;
            selectedVariates = -1; // ALL VARIATES
//            selectedVariates = 0; // SINGLE VARIATE
            percentageVariatesUsed = 1;

            mstSegmentMethod = SegmentMethods.ADHOC;
            mstSegmentParameter = 0;
            missingValueStrategy = MissingValueStrategy.VARIANCE;
        }

        //        Parameter checks
        if (qLen > maxM) throw new IllegalArgumentException("qLen > maxM");

        if (algorithmType == AlgorithmType.ST_INDEX) {
            kMeansClusters = 0;
        }

//        Initialize random
        Parameters.newRandom();

//        Build full dataPath and workloadPath
        dataPath += "/" + datasetType.toString().toLowerCase().replace("_", "-");
    }

    public static void main(String[] args) {
//        Setup logger
        configLogger(Level.INFO);
        Logger logger = Logger.getGlobal();

        // Parameters
        setParameters(args);
        setDependentParametersPreLoad();

        // Generate/load dataset
        DataManager.data = DataLoader.loadData();

        setDependentParametersPostLoad();

        // Precompute means and stds
        logger.info("Precomputing means and stds");
        DataManager.computeMeansStds();
        if (!normalize) {
            // Precompute squared sums
            logger.info("Precomputing squared sums");
            DataManager.computeSquaredSums();
        }

        // Get workload
        if (runtimeMode != RuntimeMode.INDEX) {
            logger.info("Generating workload");
            DataManager.queries = WorkloadGenerator.generateWorkload(DataManager.data, nQueries, qLen, queryNoiseEps, normalize, random);
        }

        selectedVariatesSet = new HashSet<>();
        for (int i : selectedVariatesIdx) {
            selectedVariatesSet.add(i);
        }

        DataManager.unsetVariates();

        // Print parameters
        logger.info("Parameters:");
        logger.info(Parameters.printParams());
        logger.info("============================================================================");

        Algorithm algorithm;

        switch (algorithmType) {
            case DSTREE_ORG:
            case DSTREE_DEF:
                algorithm = new MVDSTreeOrg();
                break;
            case DSTREE:
                algorithm = new MVDSTree();
                break;
            case MSEG:
                algorithm = new MSeg();
                break;
            case MASS:
                algorithm = new MVMASS();
                break;
            case ST_INDEX:
                algorithm = new MultivariateSTIndex();
                break;
            case MULTI_ST_INDEX:
                algorithm = new MultiSTIndex(true);
                break;
            case SEGMENTS_ONLY:
                algorithm = new MultiSTIndex(false);
                break;
            case BRUTE_FORCE:
                algorithm = new BruteForce();
                break;
            default:
                throw new RuntimeException("Unknown algorithm type: " + algorithmType);
        }

//        Run the algorithm
        logger.info("Starting algorithm " + algorithmType.name());
        algorithm.run();

        if (indexSize == 0){
            indexSize = algorithm.memoryUsage();
        }

        // Print
        logger.info(algorithmType.name());
        logger.info("Indexing " + indexBuildTime + "ms");
        logger.info("Querying " + queryTime + "ms");
        logger.info("Storage used: " + indexSize + " MB");
        logger.info("Dataset storage used: " + datasetSize * 8 / 1024 / 1024 + " MB");
        logger.info("Avg Subsequences Exhaustively Checked: " + subsequencesExhChecked.get() / (double) nQueries);
        logger.info("Avg Segments Under Threshold: " + segmentsUnderThreshold.get() / (double) nQueries);
        logger.info("Avg Bound Computations: " + nBoundComputations.get() / (double) nQueries);
        if (Arrays.asList(AlgorithmType.DSTREE_ORG,AlgorithmType.ST_INDEX,AlgorithmType.MULTI_ST_INDEX).contains(algorithmType)) {
            logger.info("Set-up time: " + setUpTime + "ms");
            logger.info("Index search time: " + indexSearchTime + "ms");
            logger.info("Exhaustive search time: " + exhaustiveTime + "ms");
            logger.info("-------------------- Segment stats --------------------");
            logger.info("Total number of segments: " + nSegments.get());
        }
        logger.info("-------------------- Tree stats --------------------");
        logger.info("Number of nodes: " + nNodes);
        logger.info("Number of leafs: " + nLeafs);
        logger.info("Avg leaf volume: " + totalLeafVolume / (double) nLeafs);
        logger.info("Avg leaf log volume: " + totalLeafLogVolume / (double) nLeafs);
        logger.info("Avg leaf margin: " + totalLeafMargin / (double) nLeafs);
        logger.info("Avg number of nodes visited: " + nNodesVisited / (double) nQueries);
        logger.info("Avg number of leafs visited: " + nLeafsVisited / (double) nQueries);

        // Write to file
        Parameters.saveParams();

        exit(0); // MASS might not exit by itself due to the FFT library
    }
}
