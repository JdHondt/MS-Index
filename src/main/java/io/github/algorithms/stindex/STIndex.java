package io.github.algorithms.stindex;

import io.github.algorithms.msindex.BoundedPriorityQueue;
import io.github.utils.*;
import io.github.utils.*;
import io.github.io.DataManager;
import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.Node;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Point;
import com.github.davidmoten.rtreemulti.internal.LeafDefault;
import com.github.davidmoten.rtreemulti.internal.NonLeafDefault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.*;

public class STIndex {
    final RTree<CandidateSegment, Geometry> tree;
    final int nCoeffPerDimension = fourierLengthPerDimension();

    public STIndex(int dimension, FourierTrail[] fourierTrails) {
        // Get all the segments from the dataset
        final List<Entry<CandidateSegment, Geometry>> items = lib.getStream(IntStream.range(0, N).boxed())
                .filter(n -> DataManager.supportsVariate(n, dimension)) // Filter out the time series that do not support this variate
                .flatMap(n -> DavidFalSegment.segment(n, fourierTrails[n], dimension).stream())
                .collect(Collectors.toList());

//        Insert all the segments into the R-tree
        this.tree = RTree.star().dimensions(nCoeffPerDimension).maxChildren(indexLeafSize).minChildren(indexLeafSize / 2).create(items);
    }

    public List<Tuple2<CandidateSegment, Double>> closest(Point transformedPoint, int k) {
        final BoundedPriorityQueue<Tuple2<CandidateSegment, Double>> q = new BoundedPriorityQueue<>(k, Comparator.comparingDouble(Tuple2::_2));

        for (Entry<CandidateSegment, Geometry> entry : tree.search(transformedPoint.mbr(), Double.POSITIVE_INFINITY)) {
            final CandidateSegment falItem = entry.value();

            if (!DataManager.supportsQuery(falItem.getTimeSeriesIndex())) {
                continue;
            }
            final double distance = entry.geometry().distance(transformedPoint);
            q.add(new Tuple2<>(falItem, distance));
        }
        return q.asOrderedList();
    }

    public void getBelowThreshold(double[] query, double threshold, ArrayList<SSEntry> belowThreshold, int dimension) {
        final double[] queryCoefficients = DFTUtils.computeCoefficients(query);
        final Point transformedPoint = Point.create(queryCoefficients);

        final Iterable<Entry<CandidateSegment, Geometry>> result = tree.search(transformedPoint, threshold);
        for (Entry<CandidateSegment, Geometry> entry : result) {
            final CandidateSegment falItem = entry.value();

            if (!DataManager.supportsQuery(falItem.getTimeSeriesIndex())) {
                continue;
            }

            for (int i = falItem.getStart(); i <= falItem.getEnd(); i++) {
                belowThreshold.add(new SSEntry(falItem.getTimeSeriesIndex(), i, dimension));
            }
        }
    }

    private long recursiveTreeMemoryUsage(Node<CandidateSegment, Geometry> node) {
        if (node instanceof NonLeafDefault) {
            NonLeafDefault<CandidateSegment, Geometry> nonLeaf = (NonLeafDefault<CandidateSegment, Geometry>) node;
            long memoryUsage = tree.dimensions() * 8L * 2L + 4L * 2L; // mins, maxes, 2 pointers
            for (Node<CandidateSegment, Geometry> child : nonLeaf.children()) {
                memoryUsage += recursiveTreeMemoryUsage(child);
            }
            return memoryUsage;
        } else if (node instanceof LeafDefault) {
            LeafDefault<CandidateSegment, Geometry> leaf = (LeafDefault<CandidateSegment, Geometry>) node;
            long memoryUsage = tree.dimensions() * 8L * 2L; // mins, maxes
            memoryUsage += leaf.count() * tree.dimensions() * 8L * 2L + leaf.count() * 4L * 3L; // min and max coefficients, (timeSeries, subSequence, subsequenceEnd)
            return memoryUsage;
        }
        throw new IllegalStateException("Unknown node type " + node.getClass());
    }

    public long memoryUsage() {
        return recursiveTreeMemoryUsage(tree.root().get());
    }

    private long countNodesRecursive(Node<CandidateSegment, Geometry> node) {
        if (node instanceof NonLeafDefault) {
            NonLeafDefault<CandidateSegment, Geometry> nonLeaf = (NonLeafDefault<CandidateSegment, Geometry>) node;
            long count = 1;
            for (Node<CandidateSegment, Geometry> child : nonLeaf.children()) {
                count += countNodesRecursive(child);
            }
            return count;
        } else if (node instanceof LeafDefault) {
            return 1;
        }
        throw new IllegalStateException("Unknown node type " + node.getClass());

    }

    public long countNodes() {
        return countNodesRecursive(tree.root().get());
    }
}
