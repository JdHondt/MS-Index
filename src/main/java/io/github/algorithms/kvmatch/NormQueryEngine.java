/*
 * Copyright 2018 Jiaye Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.algorithms.kvmatch;

import io.github.algorithms.kvmatch.common.NormInterval;
import io.github.algorithms.kvmatch.common.Pair;
import io.github.algorithms.kvmatch.common.QuerySegment;
import io.github.algorithms.kvmatch.common.entity.IndexNode;
import io.github.algorithms.kvmatch.utils.MeanIntervalUtils;
import io.github.io.DataManager;
import io.github.utils.CandidateSubsequence;
import io.github.utils.Tuple3;

import java.util.*;

/**
 * Query engine for KV-index_{DP} with normalization under ED
 * <p>
 * Created by Jiaye Wu on 18-1-10.
 */
public class NormQueryEngine extends KVMatchIndex {

    private static final boolean ENABLE_BETA_PARTITION = false;
    private static final double BETA_PARTITION_WIDTH = 10.0;

    public NormQueryEngine(double[] data, int n, int d) {
        super(data, n, d);
    }

    public List<CandidateSubsequence> query(List<Double> queryData, double epsilon, double alpha, double beta) {
        int queryLength = queryData.size();

        // Phase 0: calculate statistics for the query series
        // calculate mean and std of whole query series
        double ex = 0, ex2 = 0;
        for (Double value : queryData) {
            ex += value;
            ex2 += value * value;
        }
        double meanQ = ex / queryLength;
        double stdQ = Math.sqrt(ex2 / queryLength - meanQ * meanQ);
        List<QuerySegment> queries = determineQueryPlan(queryData);

        // Phase 1: index-probing
        List<NormInterval> validPositions = new ArrayList<>();  // CS

        for (int i = 0; i < queries.size(); i++) {
            QuerySegment query = queries.get(i);

            List<NormInterval> nextValidPositions = new ArrayList<>();  // CS

            // store possible current segment
            List<NormInterval> positions = new ArrayList<>();  // CS_i

            // query possible rows which mean is in distance range of i-th disjoint window
            double a = 1.0 / (alpha * alpha) * stdQ * stdQ * epsilon / windowSize;
            double beginRound = 1.0 / alpha * query.getMean() + (1 - 1.0 / alpha) * meanQ - beta - Math.sqrt(a);
            double sqrt = Math.sqrt(alpha * alpha * stdQ * stdQ * epsilon / windowSize);
            double beginRound1 = alpha * query.getMean() + (1 - alpha) * meanQ - beta - sqrt;
            beginRound = MeanIntervalUtils.toRound(Math.min(beginRound, beginRound1));

            double endRound = alpha * query.getMean() + (1 - alpha) * meanQ + beta + sqrt;
            double endRound1 = 1.0 / alpha * query.getMean() + (1 - 1.0 / alpha) * meanQ + beta + Math.sqrt(a);
            endRound = MeanIntervalUtils.toRound(Math.max(endRound, endRound1));

            // beta partitions
            int betaPartitionNum = 1;
            if (ENABLE_BETA_PARTITION) {
                betaPartitionNum = (int) (2.0 * beta / BETA_PARTITION_WIDTH);
                if (betaPartitionNum > 64) {  // at most 64 bits
                    betaPartitionNum = 64;
                }
            }
            List<Pair<Double, Double>> betaPartitions = new ArrayList<>(betaPartitionNum);
            for (int betaPartitionIdx = 0; betaPartitionIdx < betaPartitionNum; betaPartitionIdx++) {
                double betaPartition = 2.0 * beta / betaPartitionNum;

                double beginRoundT = 1.0 / alpha * query.getMean() + (1 - 1.0 / alpha) * meanQ - beta + betaPartition * betaPartitionIdx - Math.sqrt(a);
                double beginRound1T = alpha * query.getMean() + (1 - alpha) * meanQ - beta + betaPartition * betaPartitionIdx - sqrt;
                beginRoundT = MeanIntervalUtils.toRound(Math.min(beginRoundT, beginRound1T));

                double endRoundT = alpha * query.getMean() + (1 - alpha) * meanQ - beta + betaPartition * (betaPartitionIdx + 1) + sqrt;
                double endRound1T = 1.0 / alpha * query.getMean() + (1 - 1.0 / alpha) * meanQ - beta + betaPartition * (betaPartitionIdx + 1) + Math.sqrt(a);
                endRoundT = MeanIntervalUtils.toRound(Math.max(endRoundT, endRound1T));

                betaPartitions.add(new Pair<>(beginRoundT, endRoundT));
            }

            scanIndex(beginRound, endRound, positions, betaPartitions);
            positions = sortButNotMergeIntervals(positions);

            if (i == 0) {
                for (NormInterval position : positions) {
                    if (position.getRight() >= DataManager.noSubsequences(n)) {
                        if (position.getLeft() < DataManager.noSubsequences(n)) {
                            nextValidPositions.add(new NormInterval(position.getLeft() + windowSize,
                                    DataManager.noSubsequences(n) + windowSize + 1,
                                    position.getExLower(), position.getEx2Lower(), position.getBetaPartitions()));
                        }
                    } else {
                        nextValidPositions.add(new NormInterval(position.getLeft() + windowSize,
                                position.getRight() + windowSize + 1,
                                position.getExLower(), position.getEx2Lower(), position.getBetaPartitions()));
                    }
                }
            } else {
                int index1 = 0, index2 = 0;  // 1 - CS, 2 - CS_i
                while (index1 < validPositions.size() && index2 < positions.size()) {
                    if (validPositions.get(index1).getRight() < positions.get(index2).getLeft()) {
                        index1++;
                    } else if (positions.get(index2).getRight() < validPositions.get(index1).getLeft()) {
                        index2++;
                    } else {
                        long commonBetaPartitions = 0;
                        if (ENABLE_BETA_PARTITION) {
                            commonBetaPartitions = validPositions.get(index1).getBetaPartitions() & positions.get(index2).getBetaPartitions();
                            if (commonBetaPartitions == 0) {  // no common, abandon the former one
                                if (validPositions.get(index1).getRight() < positions.get(index2).getRight()) {
                                    index1++;
                                } else {
                                    index2++;
                                }
                                continue;
                            }
                        }

                        if (validPositions.get(index1).getRight() < positions.get(index2).getRight()) {
                            nextValidPositions.add(new NormInterval(
                                    Math.max(validPositions.get(index1).getLeft(), positions.get(index2).getLeft()) + windowSize,
                                    validPositions.get(index1).getRight() + windowSize,
                                    0, 0, commonBetaPartitions));
                            index1++;
                        } else {
                            nextValidPositions.add(new NormInterval(
                                    Math.max(validPositions.get(index1).getLeft(), positions.get(index2).getLeft()) + windowSize,
                                    positions.get(index2).getRight() + windowSize,
                                    0, 0, commonBetaPartitions));
                            index2++;
                        }
                    }
                }
            }

            validPositions = sortButNotMergeIntervalsAndCount(nextValidPositions);
            if (validPositions.isEmpty()) {
                break;
            }
        }

        if (validPositions.isEmpty()) {
            return Collections.emptyList();
        }

        final List<CandidateSubsequence> answers = new ArrayList<>();
        for (NormInterval position : validPositions) {
            for (int j = position.getLeft(); j <= position.getRight(); j++) {
                final int ssIndex = j - queries.size() * windowSize - 1;
                if (ssIndex >= DataManager.noSubsequences(n)) {
                    break;
                }
                answers.add(new CandidateSubsequence(n, d, ssIndex));
            }
        }
        return answers;
    }

    private void scanIndex(double begin, double end, List<NormInterval> positions, List<Pair<Double, Double>> betaPartitions) {
        List<Tuple3<Double, Double, IndexNode>> indexes = indexOperator.readIndexes(begin, end);
        for (Tuple3<Double, Double, IndexNode> entry : indexes) {
            double meanRound = entry._1();
            double meanRound2 = entry._2();
            long partitions = 0;
            if (ENABLE_BETA_PARTITION) {
                for (int betaPartitionIdx = 0; betaPartitionIdx < betaPartitions.size(); betaPartitionIdx++) {
                    Pair<Double, Double> betaPartition = betaPartitions.get(betaPartitionIdx);
                    if (betaPartition.getFirst() > meanRound) break;
                    if (betaPartition.getFirst() <= meanRound && betaPartition.getSecond() >= meanRound) {
                        partitions |= 1 << betaPartitionIdx;
                    }
                }
            }
            for (Pair<Integer, Integer> position : entry._3().getPositions()) {
                positions.add(new NormInterval(position.getFirst(), position.getSecond(),
                        meanRound * windowSize, meanRound2 * meanRound2 * windowSize, partitions));
            }
        }
    }

    private List<NormInterval> sortButNotMergeIntervals(List<NormInterval> intervals) {
        if (intervals.size() <= 1) {
            return intervals;
        }

        intervals.sort(Comparator.comparingInt(NormInterval::getLeft));

        NormInterval first = intervals.get(0);
        int start = first.getLeft();
        int end = first.getRight();
        double ex = first.getExLower();
        double ex2 = first.getEx2Lower();
        long betaPartitions = first.getBetaPartitions();

        List<NormInterval> result = new ArrayList<>();

        for (int i = 1; i < intervals.size(); i++) {
            NormInterval current = intervals.get(i);
            if (current.getLeft() - 1 < end || (current.getLeft() - 1 == end && Double.compare(current.getExLower(), ex) == 0 && Double.compare(current.getEx2Lower(), ex2) == 0)) {
                end = Math.max(current.getRight(), end);
                ex = Math.min(current.getExLower(), ex);
                ex2 = Math.min(current.getEx2Lower(), ex2);
                betaPartitions = current.getBetaPartitions() | betaPartitions;
            } else {
                result.add(new NormInterval(start, end, ex, ex2, betaPartitions));
                start = current.getLeft();
                end = current.getRight();
                ex = current.getExLower();
                ex2 = current.getEx2Lower();
                betaPartitions = current.getBetaPartitions();
            }
        }
        result.add(new NormInterval(start, end, ex, ex2, betaPartitions));

        return result;
    }

    private List<NormInterval> sortButNotMergeIntervalsAndCount(List<NormInterval> intervals) {
        if (intervals.size() <= 1) {
            return intervals;
        }

        intervals.sort(Comparator.comparingInt(NormInterval::getLeft));

        NormInterval first = intervals.get(0);
        int start = first.getLeft();
        int end = first.getRight();
        double ex = first.getExLower();
        double ex2 = first.getEx2Lower();
        long betaPartitions = first.getBetaPartitions();

        List<NormInterval> result = new ArrayList<>();

        for (int i = 1; i < intervals.size(); i++) {
            NormInterval current = intervals.get(i);

            if (current.getLeft() - 1 < end || (current.getLeft() - 1 == end && Double.compare(current.getExLower(), ex) == 0 && Double.compare(current.getEx2Lower(), ex2) == 0)) {
                end = Math.max(current.getRight(), end);
                ex = Math.min(current.getExLower(), ex);
                ex2 = Math.min(current.getEx2Lower(), ex2);
                betaPartitions = current.getBetaPartitions() | betaPartitions;
            } else {
                result.add(new NormInterval(start, end, ex, ex2, betaPartitions));
                start = current.getLeft();
                end = current.getRight();
                ex = current.getExLower();
                ex2 = current.getEx2Lower();
                betaPartitions = current.getBetaPartitions();
            }
        }
        result.add(new NormInterval(start, end, ex, ex2, betaPartitions));

        return result;
    }

}

