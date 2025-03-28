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
package io.github.algorithms.kvmatch.utils;

/**
 * Utilities for operations on the Key of KV-index
 * <p>
 * Created by Jiaye Wu on 16-8-8.
 */
public class MeanIntervalUtils {

    /**
     * Precise d to 0.5*10^(-x+1).
     * For example: x=1 -> d=0.5, x=2 -> d=0.05, etc.
     */
    private static int posOfD = 2;

    /**
     * Round float number to half integer. d = 0.5
     * For Example: 1.9 ->  1.5,  1.4 ->  1.0,  1.5 ->  1.5
     * -1.9 -> -2.0, -1.4 -> -1.5, -1.5 -> -1.5
     *
     * @param value should be rounded
     * @return rounded value
     */
    public static double toRound(double value) {
        value *= Math.pow(10, posOfD - 1);
        double intValue = Math.floor(value);
        double diff = value - intValue;
        double retValue = intValue;
        if (Double.compare(diff, 0.5) >= 0) {
            retValue += 0.5;
        }
        retValue *= Math.pow(10, -posOfD + 1);
        return retValue;
    }

    /**
     * To upper bound of mean interval.
     * For example: 1.0 ->  1.5,  1.5 ->  2.0
     * -1.0 -> -0.5, -1.5 -> -1.0
     *
     * @param round mean interval round
     * @return upper bound
     */
    public static double toUpper(double round) {
        round *= Math.pow(10, posOfD - 1);
        round += 0.5;
        round *= Math.pow(10, -posOfD + 1);
        return round;
    }

}
