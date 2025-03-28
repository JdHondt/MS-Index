/*
 * Copyright 2017 Jiaye Wu
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

import io.github.algorithms.kvmatch.common.Interval;
import io.github.algorithms.kvmatch.common.Pair;
import io.github.algorithms.kvmatch.common.QuerySegment;
import io.github.algorithms.kvmatch.common.entity.IndexNode;
import io.github.algorithms.kvmatch.utils.MeanIntervalUtils;
import io.github.io.DataManager;
import io.github.utils.CandidateSubsequence;
import io.github.utils.Tuple3;

import java.util.*;

/**
 * Query engine for KV-index_{DP}
 * <p>
 * Created by Jiaye Wu on 16-8-9.
 */
public class QueryEngine extends KVMatchIndex {
    public QueryEngine(double[] data, int n, int d) {
        super(data, n, d);
    }

    public List<CandidateSubsequence> query(List<Double> queryData, double epsilon) {
        // Phase 0: segmentation (DP)
        List<QuerySegment> queries = determineQueryPlan(queryData);

        // Phase 1: index-probing
        List<Interval> validPositions = new ArrayList<>();  // CS

        double lastMinimumEpsilon = 0;
        for (int i = 0; i < queries.size(); i++) {
            QuerySegment query = queries.get(i);

            List<Interval> nextValidPositions = new ArrayList<>();  // CS

            // store possible current segment
            List<Interval> positions = new ArrayList<>();  // CS_i

            // query possible rows which mean is in distance range of i-th disjoint window
            double range = Math.sqrt((epsilon - lastMinimumEpsilon) / windowSize);
            double beginRound = MeanIntervalUtils.toRound(query.getMean() - range);
            double endRound = MeanIntervalUtils.toRound(query.getMean() + range);

            scanIndex(beginRound, endRound, query, positions);
            positions = sortButNotMergeIntervals(positions);

            lastMinimumEpsilon = Double.MAX_VALUE;

            if (i == 0) {
                for (Interval position : positions) {
                    if (position.getRight() >= DataManager.noSubsequences(n)) { // the rightmost subsequence is out of the data
                        if (position.getLeft() < DataManager.noSubsequences(n)) { // the leftmost subsequence is in the data
                            nextValidPositions.add(new Interval(position.getLeft() + windowSize, DataManager.noSubsequences(n) + windowSize + 1, position.getEpsilon()));
                        }
                    } else {
                        nextValidPositions.add(new Interval(position.getLeft() + windowSize, position.getRight() + windowSize + 1, position.getEpsilon()));
                    }
                    if (position.getEpsilon() < lastMinimumEpsilon) {
                        lastMinimumEpsilon = position.getEpsilon();
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
                        double sumEpsilon = validPositions.get(index1).getEpsilon() + positions.get(index2).getEpsilon();
                        if (validPositions.get(index1).getRight() < positions.get(index2).getRight()) {
                            if (sumEpsilon <= epsilon) {
                                nextValidPositions.add(new Interval(Math.max(validPositions.get(index1).getLeft(), positions.get(index2).getLeft()) + windowSize, validPositions.get(index1).getRight() + windowSize, sumEpsilon));
                                if (sumEpsilon < lastMinimumEpsilon) {
                                    lastMinimumEpsilon = sumEpsilon;
                                }
                            }
                            index1++;
                        } else {
                            if (sumEpsilon <= epsilon) {
                                nextValidPositions.add(new Interval(Math.max(validPositions.get(index1).getLeft(), positions.get(index2).getLeft()) + windowSize, positions.get(index2).getRight() + windowSize, sumEpsilon));
                                if (sumEpsilon < lastMinimumEpsilon) {
                                    lastMinimumEpsilon = sumEpsilon;
                                }
                            }
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
        for (Interval position : validPositions) {
            for (int j = position.getLeft(); j <= position.getRight(); j++) {
                final int ssIndex = j - queries.size() * windowSize - 1;
                if (ssIndex >= DataManager.noSubsequences(n)) {
                    break;
                }
                answers.add(new CandidateSubsequence(n, d,ssIndex));
            }
        }
        return answers;
    }

    private void scanIndex(double begin, double end, QuerySegment query, List<Interval> positions) {
        List<Tuple3<Double, Double, IndexNode>> indexes = indexOperator.readIndexes(begin, end);
        for (Tuple3<Double, Double, IndexNode> entry : indexes) {
            double meanRoundLower = entry._1();
            double meanRoundUpper = entry._2();
            double lowerBound = getDistanceLowerBound(query, meanRoundLower, meanRoundUpper);
            for (Pair<Integer, Integer> position : entry._3().getPositions()) {
                positions.add(new Interval(position.getFirst(), position.getSecond(), windowSize * lowerBound));
            }
        }
    }

    private double getDistanceLowerBound(QuerySegment query, double meanLower, double meanUpper) {
        double delta;
        if (meanLower > query.getMean()) {
            delta = (meanLower - query.getMean()) * (meanLower - query.getMean());
        } else if (meanUpper < query.getMean()) {
            delta = (query.getMean() - meanUpper) * (query.getMean() - meanUpper);
        } else {
            delta = 0;
        }

        return delta;
    }

    private List<Interval> sortButNotMergeIntervals(List<Interval> intervals) {
        if (intervals.size() <= 1) {
            return intervals;
        }

        intervals.sort(Comparator.comparingInt(Interval::getLeft));

        Interval first = intervals.get(0);
        int start = first.getLeft();
        int end = first.getRight();
        double epsilon = first.getEpsilon();

        List<Interval> result = new ArrayList<>();

        for (int i = 1; i < intervals.size(); i++) {
            Interval current = intervals.get(i);
            if (current.getLeft() - 1 < end || (current.getLeft() - 1 == end && Math.abs(current.getEpsilon() - epsilon) < 1)) {
                end = Math.max(current.getRight(), end);
                epsilon = Math.min(current.getEpsilon(), epsilon);
            } else {
                result.add(new Interval(start, end, epsilon));
                start = current.getLeft();
                end = current.getRight();
                epsilon = current.getEpsilon();
            }
        }
        result.add(new Interval(start, end, epsilon));

        return result;
    }

    private List<Interval> sortButNotMergeIntervalsAndCount(List<Interval> intervals) {
        if (intervals.size() <= 1) {
            return intervals;
        }

        intervals.sort(Comparator.comparingInt(Interval::getLeft));

        Interval first = intervals.get(0);
        int start = first.getLeft();
        int end = first.getRight();
        double epsilon = first.getEpsilon();

        List<Interval> result = new ArrayList<>();

        for (int i = 1; i < intervals.size(); i++) {
            Interval current = intervals.get(i);

            if (current.getLeft() - 1 < end || (current.getLeft() - 1 == end && Math.abs(current.getEpsilon() - epsilon) < 1)) {
                end = Math.max(current.getRight(), end);
                epsilon = Math.min(current.getEpsilon(), epsilon);
            } else {
                result.add(new Interval(start, end, epsilon));
                start = current.getLeft();
                end = current.getRight();
                epsilon = current.getEpsilon();
            }
        }
        result.add(new Interval(start, end, epsilon));

        return result;
    }

}
