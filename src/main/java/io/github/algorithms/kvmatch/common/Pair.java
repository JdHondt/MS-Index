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
package io.github.algorithms.kvmatch.common;

import java.io.Serializable;

/**
 * A generic class for pairs.
 *
 * @param <T1>
 * @param <T2>
 */
public class Pair<T1, T2> implements Serializable {

    private static final long serialVersionUID = -8947681976815719879L;

    protected T1 first = null;
    protected T2 second = null;

    /**
     * Default constructor.
     */
    public Pair() {
    }

    /**
     * Constructor
     *
     * @param a operand
     * @param b operand
     */
    public Pair(T1 a, T2 b) {
        this.first = a;
        this.second = b;
    }

    /**
     * Constructs a new pair, inferring the type via the passed arguments
     *
     * @param <T1> type for first
     * @param <T2> type for second
     * @param a    first element
     * @param b    second element
     * @return a new pair containing the passed arguments
     */
    public static <T1, T2> Pair<T1, T2> newPair(T1 a, T2 b) {
        return new Pair<>(a, b);
    }

    private static boolean equals(Object x, Object y) {
        return (x == null && y == null) || (x != null && x.equals(y));
    }

    /**
     * Return the first element stored in the pair.
     *
     * @return T1
     */
    public T1 getFirst() {
        return first;
    }

    /**
     * Replace the first element of the pair.
     *
     * @param a operand
     */
    public void setFirst(T1 a) {
        this.first = a;
    }

    /**
     * Return the second element stored in the pair.
     *
     * @return T2
     */
    public T2 getSecond() {
        return second;
    }

    /**
     * Replace the second element of the pair.
     *
     * @param b operand
     */
    public void setSecond(T2 b) {
        this.second = b;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object other) {
        return other instanceof Pair && equals(first, ((Pair) other).first) &&
                equals(second, ((Pair) other).second);
    }

    @Override
    public int hashCode() {
        if (first == null)
            return (second == null) ? 0 : second.hashCode() + 1;
        else if (second == null)
            return first.hashCode() + 2;
        else
            return first.hashCode() * 17 + second.hashCode();
    }

    @Override
    public String toString() {
        return "{" + getFirst() + "," + getSecond() + "}";
    }
}
