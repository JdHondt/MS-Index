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
package io.github.algorithms.kvmatch.common.entity;

import io.github.algorithms.kvmatch.common.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A node of the index
 * *
 * Created by Jiaye Wu on 16-3-24.
 */
public class IndexNode implements Serializable {

    public static int MAXIMUM_DIFF = 256;

    private List<Pair<Integer, Integer>> positions;

    public IndexNode() {
        positions = new ArrayList<>(100);
    }

    @Override
    public String toString() {
        return "IndexNode{" + "positions=" + positions + '}';
    }

    public List<Pair<Integer, Integer>> getPositions() {
        return positions;
    }

    public int getMemoryUsage() {
        return positions.size() * 4 * 2;
    }

}
