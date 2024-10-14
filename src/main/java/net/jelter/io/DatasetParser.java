package net.jelter.io;

import net.jelter.utils.Parameters;
import net.jelter.utils.lib;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.jelter.utils.Parameters.*;

public class DatasetParser {
    final String directory;
    public long subsequences = 0;

    public DatasetParser(String directory) {
        this.directory = directory;
    }

    public int scanDirectory() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(directory))) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    variatePaths.add(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        Sort paths
        variatePaths.sort(Path::compareTo);

//        Only get the first d dimensions
        if (dimensions != -1 && variatePaths.size() > dimensions) {
            variatePaths = new ArrayList<>(variatePaths.subList(0, Parameters.dimensions));
        }
        return variatePaths.size();
    }

    public double[][][] parseData(int maxN, int maxM, int queryLength, int dimensions) {
        // Count nr of lines in first file
        final Path firstPath = variatePaths.get(0);
        int lines = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(firstPath.toFile()))) {
            String line = br.readLine();
            while (line != null) {
                if (!line.isEmpty()) lines++;
                line = br.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final HashSet<Integer> picked = new HashSet<>();

        if (maxN > lines) {
            maxN = lines;
            IntStream.range(0, maxN).forEach(picked::add);
        } else {
            while (picked.size() < Math.min(lines, maxN * 2)) {
                picked.add(random.nextInt(lines));
            }
        }


        final ArrayList<double[][]> data = new ArrayList<>();
        int largestM = 0;
        for (int i = 0; i < dimensions; i++) { // For each dimension
            final Path path = variatePaths.get(i);
            try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) { // Read the file
                int lineid = -1;
                int dataIndex = 0;
                for (String line : (Iterable<String>) stream::iterator) {
                    lineid++;
                    if (line.isEmpty()) continue;
                    if (dataIndex >= maxN) break;
                    if (!picked.contains(lineid)) {
                        continue;
                    }
                    final String[] split = line.split(",");
                    if (split.length < queryLength) {
//                        Make sure to pick a replacement
                        picked.remove(lineid);
                        int replacement = lineid + 1;
                        while (picked.contains(replacement)) {
                            replacement++;
                        }
                        picked.add(replacement);
                        continue;
                    }
                    final double[][] timeSeries;

                    int M = Math.min(maxM, split.length);
                    largestM = Math.max(largestM, M);
                    if (i == 0) {
                        timeSeries = new double[dimensions][M];
                        subsequences += M - queryLength + 1L;
                        data.add(timeSeries);
                    } else {
                        timeSeries = data.get(dataIndex);
                    }
                    final double[] values = timeSeries[i];
                    for (int k = 0; k < M; k++) {
                        try {
                            values[k] = Double.parseDouble(split[k]);
                        } catch (NumberFormatException e) {
                            values[k] = 0;
                        }
                    }
                    dataIndex++;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        Parameters.maxM = lib.nextPowerOfTwo(largestM);

        final double[][][] dataset = new double[data.size()][][];
        data.toArray(dataset);

        Parameters.datasetSize = 0L;
        for (double[][] timeSeries : dataset) {
            for (double[] variates : timeSeries) {
                Parameters.datasetSize += variates.length;
            }
        }

        return dataset;
    }

    public double[][][] parseWorkload(int n, int m, int d) {
        final double[][][] dataset = new double[n][d][m];
        for (int i = 0; i < n; i++) { // For each time series
//            Paths should be per time series
            final Path path = variatePaths.get(i);

//            Read the data from the file which is in column major format with a header
            try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
                String line;
                int j = 0;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) continue;
//                    Skip the header
                    if (j == 0) {
                        j++;
                        continue;
                    }

//                    End if we have enough data
                    if (j >= m) break;

//                    Parse the line
                    final String[] split = line.split(",");

//                    Skip if the line is too short (not enough data)
                    if (split.length < d) continue;

//                    Add to the dataset
                    for (int k = 0; k < d; k++) {
                        dataset[i][k][j - 1] = Double.parseDouble(split[k]);
                    }
                    j++;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return dataset;
    }

}
