package io.github.algorithms.kvmatch;

import io.github.algorithms.kvmatch.common.Pair;
import io.github.algorithms.kvmatch.common.entity.IndexNode;
import io.github.algorithms.kvmatch.operator.file.IndexFileOperator;
import io.github.algorithms.kvmatch.statistic.StatisticInfo;
import io.github.algorithms.kvmatch.utils.IndexNodeUtils;
import io.github.algorithms.kvmatch.utils.MeanIntervalUtils;
import io.github.io.DataManager;

import java.util.*;

class SingleIndexBuilder {

    IndexFileOperator indexOperator;

    double[] t;  // data array and query array

    double ex, ex2, mean, std;
    int n, d, m, w;
    double[] buffer;

    // For every EPOCH points, all cumulative values, such as ex (sum), ex2 (sum square), will be restarted for reducing the floating point error.
    int EPOCH = 100000;

    int dataIndex = 0;

    SingleIndexBuilder(int n, int d, int m, int w, IndexFileOperator indexOperator) {
        this.w = w;
        this.m = m;
        this.n = n;
        this.d = d;

        t = new double[w * 2];
        buffer = new double[EPOCH];

        this.indexOperator = indexOperator;
    }

    boolean nextData() {
        return dataIndex < DataManager.data[n][d].length;
    }

    double getCurrentData() {
        return DataManager.data[n][d][dataIndex++];
    }

    void run() {
        double point;
        boolean done = false;
        int it = 0, ep;

        double maxMean = Double.MIN_VALUE;
        Double lastMeanRound = null;
        IndexNode indexNode = null;
        Map<Double, IndexNode> indexNodeMap = new HashMap<>();

        // step 1: fixed-width index rows
        while (!done) {
            // Read first w-1 points
            if (it == 0) {
                for (int k = 0; k < w - 1; k++) {
                    if (nextData()) {
                        point = getCurrentData();
                        buffer[k] = point;
                    }
                }
            } else {
                for (int k = 0; k < w - 1; k++) {
                    buffer[k] = buffer[EPOCH - w + 1 + k];
                }
            }

            // Read buffer of size EPOCH or when all data has been read.
            ep = w - 1;
            while (ep < EPOCH) {
                if (nextData()) {
                    point = getCurrentData();
                    buffer[ep] = point;
                    ep++;
                } else {
                    break;
                }
            }

            // Data are read in chunk of size EPOCH.
            // When there is nothing to read, the loop is end.
            if (ep <= w - 1) {
                done = true;
            } else {
                // Do main task here..
                ex = 0;
                ex2 = 0;
                for (int i = 0; i < ep; i++) {
                    // A bunch of data has been read and pick one of them at a time to use
                    point = buffer[i];

                    // Calculate sum and sum square
                    ex += point;
                    ex2 += point * point;

                    // t is a circular array for keeping current data
                    t[i % w] = point;

                    // Double the size for avoiding using modulo "%" operator
                    t[(i % w) + w] = point;

                    // Start the task when there are more than m-1 points in the current chunk
                    if (i >= w - 1) {
                        mean = ex / w;
                        std = ex2 / w;
                        std = Math.sqrt(std - mean * mean);

                        if (mean > maxMean) {
                            maxMean = mean;
                        }

                        // compute the start location of the data in the current circular array, t
                        int j = (i + 1) % w;

                        // store the mean and std for current chunk
                        long loc = (it) * (EPOCH - w + 1) + i - w + 1 + 1;
                        if (loc > m) {
                            done = true;
                            break;
                        }

                        double curMeanRound = MeanIntervalUtils.toRound(mean);

                        if (lastMeanRound == null || !lastMeanRound.equals(curMeanRound) || loc - indexNode.getPositions().get(indexNode.getPositions().size() - 1).getFirst() == IndexNode.MAXIMUM_DIFF - 1) {
                            // put the last row
                            if (lastMeanRound != null) {
                                indexNodeMap.put(lastMeanRound, indexNode);
                            }
                            // new row
                            indexNode = indexNodeMap.get(curMeanRound);
                            if (indexNode == null) {
                                indexNode = new IndexNode();
                            }
                            indexNode.getPositions().add(new Pair<>((int) loc, (int) loc));
                            lastMeanRound = curMeanRound;
                        } else {
                            // use last row
                            int index = indexNode.getPositions().size();
                            indexNode.getPositions().get(index - 1).setSecond((int) loc);
                        }

                        // Reduce obsolete points from sum and sum square
                        ex -= t[j];
                        ex2 -= t[j] * t[j];
                    }
                }

                // If the size of last chunk is less then EPOCH, then no more data and terminate.
                if (ep < EPOCH) {
                    done = true;
                } else {
                    it++;
                }
            }
        }

        // put the last node
        if (indexNode != null && !indexNode.getPositions().isEmpty()) {
            indexNodeMap.put(lastMeanRound, indexNode);
        }

        // step 2: merge consecutive rows to variable-width index rows
        // get ordered statistic list and average number of disjoint window intervals
        List<Pair<Double, Pair<Integer, Integer>>> rawStatisticInfo = new ArrayList<>(indexNodeMap.size());
        StatisticInfo average = new StatisticInfo();
        for (Map.Entry<Double, IndexNode> entry : indexNodeMap.entrySet()) {
            IndexNode indexNode1 = entry.getValue();
            rawStatisticInfo.add(new Pair<>(entry.getKey(), new Pair<>(indexNode1.getPositions().size(), 0)));
            average.append(indexNode1.getPositions().size());
        }
        rawStatisticInfo.sort((o1, o2) -> -o1.getFirst().compareTo(o2.getFirst()));

        // merge adjacent index nodes satisfied criterion, and put to HBase
        Map<Double, IndexNode> indexStore = new TreeMap<>();
        IndexNode last = indexNodeMap.get(rawStatisticInfo.get(0).getFirst());
        for (int i = 1; i < rawStatisticInfo.size(); i++) {
            IndexNode current = indexNodeMap.get(rawStatisticInfo.get(i).getFirst());
            boolean isMerged = false;
            if (rawStatisticInfo.get(i).getSecond().getFirst() < average.getAverage() * 1.2) {
                IndexNode merged = IndexNodeUtils.mergeIndexNode(last, current);
                if (merged.getPositions().size() < (last.getPositions().size() + current.getPositions().size()) * 0.8) {
                    last = merged;
                    isMerged = true;
                }
            }
            if (!isMerged) {
                double key = rawStatisticInfo.get(i - 1).getFirst();
                indexStore.put(key, last);

                last = current;
            }
        }
        double key = rawStatisticInfo.get(rawStatisticInfo.size() - 1).getFirst();
        indexStore.put(key, last);

        indexOperator.writeAll(indexStore, maxMean);
    }
}
