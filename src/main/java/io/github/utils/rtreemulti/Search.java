package io.github.utils.rtreemulti;

import io.github.utils.Parameters;
import io.github.utils.RunningThreshold;
import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.geometry.Rectangle;

import java.io.Serializable;
import java.util.*;

final class Search {

    private Search() {
        // prevent instantiation
    }

    static <T extends Serializable, S extends Geometry> Iterable<Entry<T, S>> search(Node<T, S> node, Rectangle geometry, RunningThreshold maxDistance) {
        return new SearchIterable<>(node, geometry, maxDistance);
    }

    static final class SearchIterable<T extends Serializable, S extends Geometry> implements Iterable<Entry<T, S>> {

        private final Node<T, S> node;
        private final Rectangle geometry;
        private final RunningThreshold maxDistance;

        SearchIterable(Node<T, S> node, Rectangle geometry, RunningThreshold maxDistance) {
            this.node = node;
            this.geometry = geometry;
            this.maxDistance = maxDistance;
        }

        @Override
        public Iterator<Entry<T, S>> iterator() {
            return new SearchIterator<>(node, geometry, maxDistance);
        }

    }

    static final class SearchIterator<T extends Serializable, S extends Geometry> implements Iterator<Entry<T, S>> {

        private final Rectangle geometry;
        private final RunningThreshold maxDistance;
        private final PriorityQueue<NodePosition<T, S>> stack;
        private Entry<T, S> next;

        SearchIterator(Node<T, S> node, Rectangle geometry, RunningThreshold maxDistance) {
            this.geometry = geometry;
            this.maxDistance = maxDistance;
            this.stack = new PriorityQueue<>(Comparator.comparingDouble(NodePosition::getPriority));
            stack.add(new NodePosition<T, S>(node, 0, 0));
        }

        @Override
        public boolean hasNext() {
            load();
            return next != null;
        }

        @Override
        public Entry<T, S> next() {
            load();
            if (next == null) {
                throw new NoSuchElementException();
            } else {
                Entry<T, S> v = next;
                next = null;
                return v;
            }
        }

        private void load() {
            if (next == null) {
                next = search();
            }
        }

        private double distance(Node<T, S> node) {
            return node.geometry().distance(geometry);
        }

        private Entry<T, S> search() {
            while (!stack.isEmpty()) {
                NodePosition<T, S> np = stack.peek();
                //            Check if the leaf is still under the max distance, if not, abandon the search as the rest of the nodes will be further away
                if (np.getPriority() > maxDistance.get()) {
                    break;
                }

                if (!np.hasRemaining()) {
                    // handle after last in node
                    searchAfterLastInNode();
                } else if (!np.node().isLeaf()) {
                    // handle non-leaf
                    searchNonLeaf(np);
                } else {

                    // handle leaf
                    Entry<T, S> v = searchLeaf(np);
                    if (v != null) {
                        return v;
                    }
                }
            }
            return null;
        }

        private Entry<T, S> searchLeaf(NodePosition<T, S> np) {
            int i = np.position();
            Leaf<T, S> leaf = (Leaf<T, S>) np.node();
            do {
                Entry<T, S> entry = leaf.entry(i);

                double distance = entry.geometry().distance(geometry);

                if (distance <= maxDistance.get()) {
                    np.setPosition(i + 1);
                    return entry;
                }
                i++;
            } while (i < leaf.count());
            np.setPosition(i);
            return null;
        }

        private void searchNonLeaf(NodePosition<T, S> np) {
            Node<T, S> child = ((NonLeaf<T, S>) np.node()).child(np.position());

            double distance = distance(child);
            if (distance <= maxDistance.get()) {
                stack.add(new NodePosition<>(child, 0, distance));

            }
            np.setPosition(np.position() + 1);
        }

        private void searchAfterLastInNode() {
            NodePosition<T,S> node = stack.poll();
            if (node.node().isLeaf()) {
                Parameters.nLeafsVisited++;
            } else{
                Parameters.nNodesVisited++;
            }
        }

    }

}
