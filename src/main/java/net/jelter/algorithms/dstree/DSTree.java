package net.jelter.algorithms.dstree;

import net.jelter.utils.TeunTuple2;

import java.util.*;

public class DSTree {
    Node root;
    final PriorityQueue<QueueTuple> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueTuple::distance));

    public DSTree(int capacity, int subsequenceLength) {
        final double[][] initialEndPoints = {{subsequenceLength, 0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY}};
        root = new LeafNode(initialEndPoints, capacity, true);
    }

    public void insert(DSSubSequence ss) {
        if (root.isFull()) {
            root = root.convertToInternalNode(ss);
        } else {
            root.insert(ss);
        }
    }

    public void underThreshold(double tau, SubSequence seq, ArrayList<TeunTuple2> results, double[] query) {
        queue.clear();
        queue.add(new QueueTuple(0, root));
        while (!queue.isEmpty()) {
            final QueueTuple tuple = queue.poll();
            final Node node = tuple.node();
            final double minDist = tuple.distance();

            if (minDist > tau) {
                break;
            }

            if (node instanceof LeafNode) {
                LeafNode leafNode = (LeafNode) node;
                for (DSSubSequence subSequence : leafNode.getSubSequences()) {
                    if (subSequence.distance(query) < tau) {
                        results.add(subSequence.getIdentifier());
                    }
                }
            } else {
                final InternalNode internalNode = (InternalNode) node;
                queue.add(new QueueTuple(internalNode.left.minimumDistance(seq), internalNode.left));
                queue.add(new QueueTuple(internalNode.right.minimumDistance(seq), internalNode.right));
            }
        }
    }

    public ArrayList<DSSubSequence> getBest(SubSequence query) {
        return root.getBest(query);
    }
}
