package io.github.algorithms.kvmatch;

import io.github.algorithms.kvmatch.common.QuerySegment;
import io.github.algorithms.kvmatch.operator.file.IndexFileOperator;
import io.github.utils.Parameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class KVMatchIndex implements Serializable {
    final int windowSize;
    final IndexFileOperator indexOperator = new IndexFileOperator();
    int n;
    int d;
    int m;

    KVMatchIndex(double[] data, int n, int d) {
        this.n = n;
        this.d = d;
        this.m = data.length;
        this.windowSize = (int) Math.floor(Parameters.qLen / Parameters.kvMatchSegmentDivisor);
        buildIndexes();
    }

    private void buildIndexes() {
        SingleIndexBuilder builder = new SingleIndexBuilder(n, d, m, windowSize, indexOperator);
        builder.run();
    }

    List<QuerySegment> determineQueryPlan(List<Double> queryData) {
        // calculate mean and sum of each disjoint window
        List<QuerySegment> queries = new ArrayList<>();
        double ex = 0;
        for (int i = 0; i < queryData.size(); i++) {
            ex += queryData.get(i);
            if ((i + 1) % windowSize == 0) {
                queries.add(new QuerySegment(ex / windowSize));
                ex = 0;
            }
        }
        return queries;
    }

//    public int memoryUsage() {
//        return indexOperator.memoryUsage();
//    }
}
