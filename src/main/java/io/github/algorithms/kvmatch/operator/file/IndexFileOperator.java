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
package io.github.algorithms.kvmatch.operator.file;

import io.github.algorithms.kvmatch.common.entity.IndexNode;
import io.github.utils.Tuple3;

import java.io.Serializable;
import java.util.*;

/**
 * A wrapper class to interact with the index file.
 * <p>
 * Created by Jiaye Wu on 17-8-24.
 */
public class IndexFileOperator implements Serializable {
    private Map<Double, IndexNode> sortedIndexes;
    private double maxMean;

    public List<Tuple3<Double, Double, IndexNode>> readIndexes(double keyFrom, double keyTo) {
        final List<Tuple3<Double, Double, IndexNode>> indexes = new ArrayList<>();
        final List<Double> keys = new ArrayList<>(this.sortedIndexes.keySet());
        for (int i = 0; i < this.sortedIndexes.size(); i++) {
            final double currentKey = keys.get(i);
            final double next = i + 1 < this.sortedIndexes.size() ? keys.get(i + 1) : maxMean;
            if (currentKey < keyFrom && next < keyFrom) continue;
            if (currentKey > keyTo) break;
            indexes.add(new Tuple3<>(currentKey, next, this.sortedIndexes.get(currentKey)));
        }
        return indexes;
    }

    public void writeAll(Map<Double, IndexNode> sortedIndexes, double maxMean) {
        this.sortedIndexes = sortedIndexes;
        this.maxMean = maxMean;
    }

//    public int memoryUsage() {
//        int memory = 0;
//        for (IndexNode indexNode : sortedIndexes.values()) {
//            memory += indexNode.getMemoryUsage(); // for the value
//            memory += 8;  // for the key
//        }
//        memory += 8;  // for the maxMean
//        return memory;
//    }
}
