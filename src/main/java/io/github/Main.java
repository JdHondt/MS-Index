package io.github;

import io.github.algorithms.Algorithm;
import io.github.algorithms.AlgorithmType;
import io.github.algorithms.BruteForce;
import io.github.algorithms.dstree_org.MVDSTreeOrg;
import io.github.algorithms.kvmatch.MultivariateKVMatch;
import io.github.algorithms.msindex.MSIndex;
import io.github.io.DataLoader;
import io.github.io.DataManager;
import io.github.io.WorkloadGenerator;
import io.github.algorithms.mass.MVMASS;
import io.github.algorithms.stindex.MultivariateSTIndex;
import io.github.utils.Parameters;

import java.util.*;
import java.util.logging.*;

import static java.lang.System.exit;
import static io.github.io.DataManager.selectedVariatesSet;
import static io.github.utils.Parameters.*;

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
            dataPath = args[i];
            i++;
            final int parsedMaxN = Integer.parseInt(args[i]);
            N = parsedMaxN == -1 ? Integer.MAX_VALUE : parsedMaxN;
            i++;
            maxM = Integer.parseInt(args[i]);
            i++;
            final int parsedQD = Integer.parseInt(args[i]);
            channels = parsedQD == -1 ? Integer.MAX_VALUE : parsedQD;
            i++;
            nQueryChannels = Integer.parseInt(args[i]);
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
            experimentId = Integer.parseInt(args[i]);
            i++;
            seed = Integer.parseInt(args[i]);
            i++;
            parallel = Boolean.parseBoolean(args[i]);
            i++;
            queryFromIndexed = Boolean.parseBoolean(args[i]);
            i++;
            queryNoiseEps = Double.parseDouble(args[i]);
        } else {
            Logger.getGlobal().info("Using default parameters");
            algorithmType = AlgorithmType.MSINDEX;
            dataPath = "/home/jens/tue/data/MTS/subsequence_search/preprocessed/stocks";
            N = 1000;
            channels = -1;
            nQueryChannels = -1; // ALL VARIATES
            qLen = 730;
            maxM = 32000;
            K = 1;
            normalize = false;
            nQueries = 100;
            runtimeMode = RuntimeMode.FULL_NO_STORE;
            experimentId = 0;
            seed = 0;
            parallel = false;
            queryFromIndexed = true;
            queryNoiseEps = 0.1;
        }

        //        Parameter checks
        if (qLen > maxM) throw new IllegalArgumentException("qLen > maxM");

        if (algorithmType == AlgorithmType.ST_INDEX) {
            kMeansClusters = 0;
        }

//        Initialize random
        Parameters.newRandom();
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
            if (queryFromIndexed) {
                DataManager.queries = WorkloadGenerator.generateWorkload(DataManager.data, nQueries, qLen, queryNoiseEps, queryFromIndexed, normalize, random);
            } else {
                DataManager.queries = WorkloadGenerator.generateWorkload(DataLoader.withheldTimeSeries, nQueries, qLen, 0, queryFromIndexed, normalize, random);
            }
        }

        selectedVariatesSet = new HashSet<>();
        for (int i : selectedVariatesIdx) {
            selectedVariatesSet.add(i);
        }

        // Print parameters
        logger.info("Parameters:");
        logger.info(Parameters.printParams());
        logger.info("============================================================================");

        Algorithm algorithm;

        switch (algorithmType) {
            case DSTREE:
                algorithm = new MVDSTreeOrg();
                break;
            case MASS:
                algorithm = new MVMASS();
                break;
            case ST_INDEX:
                algorithm = new MultivariateSTIndex();
                break;
            case MSINDEX:
                algorithm = new MSIndex(true);
                break;
            case BRUTE_FORCE:
                algorithm = new BruteForce();
                break;
            case KV_MATCH:
                algorithm = new MultivariateKVMatch();
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
        if (Arrays.asList(AlgorithmType.DSTREE,AlgorithmType.ST_INDEX,AlgorithmType.MSINDEX).contains(algorithmType)) {
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
