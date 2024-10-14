package io.github.utils.rtreemulti;

import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.guavamini.Preconditions;
import io.github.utils.Parameters;
import io.github.utils.RunningThreshold;
import io.github.utils.lib;
import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.geometry.HasGeometry;
import io.github.utils.rtreemulti.geometry.Point;
import io.github.utils.rtreemulti.geometry.Rectangle;
import io.github.utils.rtreemulti.internal.LeafDefault;
import io.github.utils.rtreemulti.internal.NodeAndEntries;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static io.github.utils.Parameters.*;

/**
 * Immutable in-memory 2D R-Tree with configurable splitter heuristic.
 * 
 * @param <T>
 *            the entry value type
 * @param <S>
 *            the entry geometry type
 */
public final class RTree<T extends Serializable, S extends Geometry> implements Serializable {

    private transient Optional<? extends Node<T, S>> root;
    private final Context<T, S> context;

    /**
     * Benchmarks show that this is a good choice for up to O(10,000) entries when
     * using Quadratic splitter (Guttman).
     */
    public static final int MAX_CHILDREN_DEFAULT_GUTTMAN = 4;

    /**
     * Benchmarks show that this is the sweet spot for up to O(10,000) entries when
     * using R*-tree heuristics.
     */
    public static final int MAX_CHILDREN_DEFAULT_STAR = 4;

    /**
     * Current size in Entries of the RTree.
     */
    private final int size;

    /**
     * Constructor.
     * 
     * @param root
     *            the root node of the tree if present
     * @param context
     *            options for the R-tree
     */
    public RTree(Optional<? extends Node<T, S>> root, int size, Context<T, S> context) {
        this.root = root;
        this.size = size;
        this.context = context;
    }

    /**
     * Constructor.
     *
     * @param root
     *            the root node of the R-tree
     * @param context
     *            options for the R-tree
     */
    private RTree(Node<T, S> root, int size, Context<T, S> context) {
        this(of(root), size, context);
    }

    /**
     * Returns a new Builder instance for a 2 dimensional {@link RTree}. Defaults to
     * maxChildren=128, minChildren=64, splitter=QuadraticSplitter.
     * 
     * @param <T>
     *            the value type of the entries in the tree
     * @param <S>
     *            the geometry type of the entries in the tree
     * @return a new RTree instance
     */
    public static <T extends Serializable, S extends Geometry> RTree<T, S> create() {
        return new Builder().create();
    }
    
    /**
     * Returns a new Builder instance for {@link RTree}. Defaults to
     * maxChildren=128, minChildren=64, splitter=QuadraticSplitter.
     * 
     * @param dimensions 
     *            the number of dimensions
     * @param <T>
     *            the value type of the entries in the tree
     * @param <S>
     *            the geometry type of the entries in the tree
     * @return a new RTree instance
     */
    public static <T extends Serializable, S extends Geometry> RTree<T, S> create(int dimensions) {
        return new Builder().dimensions(dimensions).create();
    }

    public static Builder dimensions(int dimensions) {
        return new Builder().dimensions(dimensions);
    }

    /**
     * The tree is scanned for depth and the depth returned. This involves recursing
     * down to the leaf level of the tree to get the current depth. Should be
     * <code>log(n)</code> in complexity.
     * 
     * @return depth of the R-tree
     */
    public int calculateDepth() {
        return calculateDepth(root);
    }

    private static <T extends Serializable, S extends Geometry> int calculateDepth(Optional<? extends Node<T, S>> root) {
        if (!root.isPresent())
            return 0;
        else
            return calculateDepth(root.get(), 0);
    }

    private static <T extends Serializable, S extends Geometry> int calculateDepth(Node<T, S> node, int depth) {
        if (node.isLeaf())
            return depth + 1;
        else
            return calculateDepth(((NonLeaf<T, S>) node).child(0), depth + 1);
    }

    /**
     * When the number of children in an R-tree node drops below this number the
     * node is deleted and the children are added on to the R-tree again.
     * 
     * @param minChildren
     *            less than this number of children in a node triggers a node
     *            deletion and redistribution of its members
     * @return builder
     */
    public static Builder minChildren(int minChildren) {
        return new Builder().minChildren(minChildren);
    }

    /**
     * Sets the max number of children in an R-tree node.
     * 
     * @param maxChildren
     *            max number of children in an R-tree node
     * @return builder
     */
    public static Builder maxChildren(int maxChildren) {
        return new Builder().maxChildren(maxChildren);
    }

    public static Builder leafSize(int leafSize) {
        return new Builder().leafSize(leafSize);
    }

    /**
     * Sets the {@link Splitter} to use when maxChildren is reached.
     * 
     * @param splitter
     *            the splitter algorithm to use
     * @return builder
     */
    public static Builder splitter(Splitter splitter) {
        return new Builder().splitter(splitter);
    }

    /**
     * Sets the node {@link Selector} which decides which branches to follow when
     * inserting or searching.
     * 
     * @param selector
     *            determines which branches to follow when inserting or searching
     * @return builder
     */
    public static Builder selector(Selector selector) {
        return new Builder().selector(selector);
    }

    /**
     * Sets the splitter to {@link SplitterRStar} and selector to
     * {@link SelectorRStar} and defaults to minChildren=10.
     * 
     * @return builder
     */
    public static Builder star() {
        return new Builder().star();
    }

    /**
     * RTree Builder.
     */
    public static class Builder {

        /**
         * According to http://dbs.mathematik.uni-marburg.de/publications/myPapers
         * /1990/BKSS90.pdf (R*-tree paper), best filling ratio is 0.4 for both
         * quadratic split and R*-tree split.
         */
        private static final double DEFAULT_FILLING_FACTOR = 0.4;
        private static final double DEFAULT_LOADING_FACTOR = 0.7;
        private Optional<Integer> maxChildren = empty();
        private Optional<Integer> minChildren = empty();
        private Optional<Integer> leafSize = empty();
        private Splitter splitter = SplitterQuadratic.INSTANCE;
        private Selector selector = SelectorMinimalVolumeIncrease.INSTANCE;
        private double loadingFactor = DEFAULT_LOADING_FACTOR;
        private boolean star = false;
        private Factory<Serializable, Geometry> factory = Factory.defaultFactory();
        private int dimensions = 2;

        private Builder() {
        }
        
        public Builder dimensions(int dimensions) {
            Preconditions.checkArgument(dimensions >= 2, "dimensions must be 2 or more");
            this.dimensions = dimensions;
            return this;
        }

        /**
         * The factor used as the fill ratio during bulk loading. Default is 0.7.
         * 
         * @param factor
         *            loading factor
         * @return this
         */
        public Builder loadingFactor(double factor) {
            this.loadingFactor = factor;
            return this;
        }

        /**
         * When the number of children in an R-tree node drops below this number the
         * node is deleted and the children are added on to the R-tree again.
         * 
         * @param minChildren
         *            less than this number of children in a node triggers a
         *            redistribution of its children.
         * @return builder
         */
        public Builder minChildren(int minChildren) {
            this.minChildren = of(minChildren);
            return this;
        }

        /**
         * Sets the max number of children in an R-tree node.
         * 
         * @param maxChildren
         *            max number of children in R-tree node.
         * @return builder
         */
        public Builder maxChildren(int maxChildren) {
            this.maxChildren = of(maxChildren);
            return this;
        }

        public Builder leafSize(int leafSize) {
            this.leafSize = of(leafSize);
            return this;
        }

        /**
         * Sets the {@link Splitter} to use when maxChildren is reached.
         * 
         * @param splitter
         *            node splitting method to use
         * @return builder
         */
        public Builder splitter(Splitter splitter) {
            this.splitter = splitter;
            return this;
        }

        /**
         * Sets the node {@link Selector} which decides which branches to follow when
         * inserting or searching.
         * 
         * @param selector
         *            selects the branch to follow when inserting or searching
         * @return builder
         */
        public Builder selector(Selector selector) {
            this.selector = selector;
            return this;
        }

        /**
         * Sets the splitter to {@link SplitterRStar} and selector to
         * {@link SelectorRStar} and defaults to minChildren=10.
         * 
         * @return builder
         */
        public Builder star() {
            selector = SelectorRStar.INSTANCE;
            splitter = SplitterRStar.INSTANCE;
            star = true;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder factory(Factory<?, ? extends Geometry> factory) {
            // TODO could change the signature of Builder to have types to
            // support this method but would be breaking change for existing
            // clients
            this.factory = (Factory<Serializable, Geometry>) factory;
            return this;
        }

        /**
         * Builds the {@link RTree}.
         * 
         * @param <T>
         *            value type
         * @param <S>
         *            geometry type
         * @return RTree
         */
        @SuppressWarnings("unchecked")
        public <T extends Serializable, S extends Geometry> RTree<T, S> create() {
            setDefaultCapacity();

            return new RTree<T, S>(Optional.<Node<T, S>>empty(), 0, new Context<T, S>(dimensions, minChildren.get(),
                    maxChildren.get(), leafSize.get(), selector, splitter, (Factory<T, S>) factory));
        }

        /**
         * Create an RTree by bulk loading, using the STR method. STR: a simple and
         * efficient algorithm for R-tree packing
         * http://ieeexplore.ieee.org/abstract/document/582015/
         * <p>
         * Note: this method mutates the input entries, the internal order of the List
         * may be changed.
         * </p>
         * 
         * @param entries
         *            entries to be added to the r-tree
         * @return a loaded RTree
         */
        @SuppressWarnings("unchecked")
        public <T extends Serializable, S extends Geometry> RTree<T, S> create(List<Entry<T, S>> entries) {
            setDefaultCapacity();

            Context<T, S> context = new Context<T, S>(dimensions, minChildren.get(), maxChildren.get(), leafSize.get(), selector, splitter,
                    (Factory<T, S>) factory);
            return packingSTR(entries, true, entries.size(), context);
        }

        private void setDefaultCapacity() {
            if (!maxChildren.isPresent())
                if (star)
                    maxChildren = of(MAX_CHILDREN_DEFAULT_STAR);
                else
                    maxChildren = of(MAX_CHILDREN_DEFAULT_GUTTMAN);
            if (!minChildren.isPresent())
                minChildren = of((int) Math.round(maxChildren.get() * DEFAULT_FILLING_FACTOR));
            if (!leafSize.isPresent() || leafSize.get() == 0)
                leafSize = maxChildren;
        }

    //        Original split code by the creator of the library
        private <T extends Serializable, S extends Geometry> List<List<? extends HasGeometry>> _split(List<? extends HasGeometry> objects, Context<T, S> context,
                                                                                    int nodeCount, int capacity) {
            int nodePerSlice = (int) Math.ceil(Math.sqrt(nodeCount));
            int sliceCapacity = nodePerSlice * capacity;
            int sliceCount = (int) Math.ceil(1.0 * objects.size() / sliceCapacity);
            Collections.sort(objects, new MidComparator((short) 0));

            List<List<? extends HasGeometry>> slices = new ArrayList<>(nodeCount);
            for (int s = 0; s < sliceCount; s++) {
                @SuppressWarnings("rawtypes")
                List slice = new ArrayList<>(objects.subList(s * sliceCapacity, Math.min((s + 1) * sliceCapacity, objects.size())));
                slice.sort(new MidComparator((short) 1));

                for (int i = 0; i < slice.size(); i += capacity) {
                    List<Entry<T, S>> entries = new ArrayList<>(slice.subList(i, Math.min(slice.size(), i + capacity)));
                    slices.add(entries);
                }
            }
            return slices;
        }



        private <T extends Serializable, S extends Geometry> List<List<? extends HasGeometry>> split(List<? extends HasGeometry> objects, Context<T, S> context,
                                                                                int[] nSplits, int[] splitOrder, int i, int capacity) {

//            Terminating condition 1: ran out of objects
            if (objects.isEmpty()) {
                return Collections.emptyList();
            } else if (objects.size() == 1){
                return Collections.singletonList(objects);
            }

//            Terminating condition 2: reached leaf level or ran out of dimensions
            if (objects.size() <= capacity || i > context.dimensions()) {
                return Collections.singletonList(objects);
            }

            final int sortDim = splitOrder[i];
            final int nSlice = nSplits[sortDim];

//            --------------- Method 1: split based on counts by sorting ---------------------------
            objects.sort(new MidComparator(sortDim));

//            Split into divFactor slices
            List<List<? extends HasGeometry>> slices = new ArrayList<>(nSlice);
            int sliceSize = (int) Math.ceil(1.0 * objects.size() / nSlice);
            for (int j = 0; j < nSlice; j++) {
                int start = j * sliceSize;
                if (start >= objects.size()) break;
                int end = Math.min((j + 1) * sliceSize, objects.size());
                slices.addAll(split(new ArrayList<>(objects.subList(start, end)), context, nSplits, splitOrder, i + 1, capacity));
            }

            return slices;
        }

        private <T extends Serializable, S extends Geometry> double[] equalWeights(List<? extends HasGeometry> objects, Context<T, S> context){
            final double[] weights = new double[context.dimensions()];
            Arrays.fill(weights, 1.0 / context.dimensions());
            return weights;
        }

        private <T extends Serializable, S extends Geometry> double[] rangeBasedWeights(List<? extends HasGeometry> objects, Context<T, S> context){

            //   Get ranges of the dimensions (for splitting later)
            final double[] mins = lib.getStream(objects).map(hg -> hg.geometry().mbr().mins())
                    .reduce(lib::minimum).get();
            final double[] maxes = lib.getStream(objects).map(hg -> hg.geometry().mbr().maxes())
                    .reduce(lib::maximum).get();

            //            Derive the weights of the dimensions
            final double[] weights = new double[context.dimensions()];
            double sum = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = maxes[i] - mins[i];
                sum += weights[i];
            }

//            Normalize
            for (int i = 0; i < weights.length; i++) {
                weights[i] /= sum;
            }

            return weights;
        }

        private <T extends Serializable, S extends Geometry> double[] varianceBasedWeights(List<? extends HasGeometry> objects, Context<T,S> context){

            //   Get ranges of the dimensions (for splitting later)
            final double[][] mids = new double[context.dimensions()][objects.size()];
            for (int i = 0; i < objects.size(); i++) {
                HasGeometry hg = objects.get(i);
                double[] locMids = hg.geometry().mbr().mids();
                for (int j = 0; j < locMids.length; j++) {
                    mids[j][i] = locMids[j];
                }
            }

            //            Derive the weights of the dimensions
            final double[] vars = new double[context.dimensions()];
            double sum = 0;
            for (int i = 0; i < mids.length; i++) {
                vars[i] = lib.variance(mids[i]);
                sum += vars[i];
            }

//            Normalize
            for (int i = 0; i < vars.length; i++) {
                vars[i] /= sum;
            }

            return vars;
        }

        private <T extends Serializable, S extends Geometry> int[] weightsToSplits(double[] weights, Context<T,S> context, int nodeCount){
//            Derive the number of splits per dimension
            final int[] nSlices = new int[context.dimensions()];
            int expNodes = 1;
            for (int i = 0; i < nSlices.length; i++) {
//                    k1, ..., kn s.t. 1 * k1 * ... * kn = nodeCount
                nSlices[i] = (int) Math.round(FastMath.pow(nodeCount, weights[i]));
                expNodes *= nSlices[i];
            }

//            If the desired number of nodes is not reached, increase the number of splits by adding splits, starting at the best dimension
            final int[] argsortedWeights = lib.argsort(weights, false);
            while (expNodes < nodeCount) {
                for (int i: argsortedWeights) {
                    if (nSlices[i] > 1) continue;

                    nSlices[i] += 1;
                    expNodes *= nSlices[i];
                    if (expNodes >= nodeCount) break;
                }
            }

            return nSlices;
        }

        @SuppressWarnings("unchecked")
        private <T extends Serializable, S extends Geometry> RTree<T, S> packingSTR(List<? extends HasGeometry> objects, boolean isLeaf,
                int size, Context<T, S> context) {
            int capacity = isLeaf ? context.leafSize() : context.maxChildren();
//            int capacity = (int) Math.round(maxChildren * loadingFactor);
            int nodeCount = (int) Math.ceil(1.0 * objects.size() / capacity);

            if (nodeCount == 0) {
                return create();
            } else if (nodeCount == 1) {
                Node<T, S> root;
                if (isLeaf) {
                    Parameters.nLeafs++;
                    root = context.factory().createLeaf((List<Entry<T, S>>) objects, context);
                } else {
                    Parameters.nNodes++;
                    root = context.factory().createNonLeaf((List<Node<T, S>>) objects, context);
                }
                return new RTree<>(of(root), size, context);
            }


//            Determine the number of partitions/slices on each dimension based on the distance-based weights
            double[] weights;
            switch (partitionStrategy) {
                case EQUAL: weights = equalWeights(objects, context); break;
                case RANGE: weights = rangeBasedWeights(objects, context); break;
                case VARIANCE: weights = varianceBasedWeights(objects, context); break;
                default: throw new IllegalArgumentException("Unknown missing value strategy: " + partitionStrategy);
            }
            final int[] nSplits = weightsToSplits(weights, context, nodeCount);
            final int[] splitOrder = lib.argsort(nSplits, false);

            if (isLeaf){
                Logger.getGlobal().info("Split distribution:" + Arrays.toString(nSplits));
            }

            List<List<? extends HasGeometry>> slices = split(objects, context, nSplits, splitOrder, 0, capacity);

//            Old code
//            List<List<? extends HasGeometry>> slices = _split(objects, context, nodeCount, capacity);

//            Turn into nodes
            List<Node<T, S>> nodes = new ArrayList<>(slices.size());
            if (isLeaf) {
                for (List<? extends HasGeometry> slice : slices) {
                    Parameters.nLeafs++;
                    Node<T,S> leaf = context.factory().createLeaf((List<Entry<T, S>>) slice, context);

                    Parameters.totalLeafVolume += leaf.geometry().mbr().volume();
                    Parameters.totalLeafLogVolume += leaf.geometry().mbr().logVolume();
                    Parameters.totalLeafMargin += leaf.geometry().mbr().margin();
                    nodes.add(leaf);
                }
            } else {
                for (List<? extends HasGeometry> slice : slices) {
                    Parameters.nNodes++;
                    Node<T,S> node = context.factory().createNonLeaf((List<Node<T, S>>) slice, context);
                    nodes.add(node);
                }
            }

            return packingSTR(nodes, false, size, context);
        }

        private static final class MidComparator implements Comparator<HasGeometry> {
            private final int dimension; // leave space for multiple dimensions, 0 for x, 1 for y,
                                           // ...

            public MidComparator(int dimension) {
                this.dimension = dimension;
            }

            @Override
            public int compare(HasGeometry o1, HasGeometry o2) {
                return Double.compare(o1.geometry().mbr().mid(dimension), o2.geometry().mbr().mid(dimension));
            }
        }

    }

    /**
     * Returns an immutable copy of the RTree with the addition of given entry.
     * 
     * @param entry
     *            item to add to the R-tree.
     * @return a new immutable R-tree including the new entry
     */
    @SuppressWarnings("unchecked")
    public RTree<T, S> add(Entry<? extends T, ? extends S> entry) {
        Preconditions.checkArgument(dimensions() == entry.geometry().dimensions(),
                entry + " has wrong number of dimensions, expected " + dimensions());
        if (root.isPresent()) {
            List<Node<T, S>> nodes = root.get().add(entry);
            Node<T, S> node;
            if (nodes.size() == 1) {
                node = nodes.get(0);
            } else {
                node = context.factory().createNonLeaf(nodes, context);
            }
            return new RTree<T, S>(node, size + 1, context);
        } else {
            Leaf<T, S> node = context.factory().createLeaf(Lists.newArrayList((Entry<T, S>) entry), context);
            return new RTree<T, S>(node, size + 1, context);
        }
    }

    /**
     * Returns an immutable copy of the RTree with the addition of an entry
     * comprised of the given value and Geometry.
     * 
     * @param value
     *            the value of the {@link Entry} to be added
     * @param geometry
     *            the geometry of the {@link Entry} to be added
     * @return a new immutable R-tree including the new entry
     */
    public RTree<T, S> add(T value, S geometry) {
        return add(context.factory().createEntry(value, geometry));
    }

    /**
     * Returns an immutable RTree with the current entries and the additional
     * entries supplied as a parameter.
     * 
     * @param entries
     *            entries to add
     * @return R-tree with entries added
     */
    public RTree<T, S> add(Iterable<Entry<T, S>> entries) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries) {
            tree = tree.add(entry);
        }
        return tree;
    }

    /**
     * Returns a new R-tree with the given entries deleted. If <code>all</code> is
     * false deletes only one if exists. If <code>all</code> is true deletes all
     * matching entries.
     * 
     * @param entries
     *            entries to delete
     * @param all
     *            if false deletes one if exists else deletes all
     * @return R-tree with entries deleted
     */
    public RTree<T, S> delete(Iterable<Entry<T, S>> entries, boolean all) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries)
            tree = tree.delete(entry, all);
        return tree;
    }

    /**
     * Returns a new R-tree with the given entries deleted but only one matching
     * occurence of each entry is deleted.
     * 
     * @param entries
     *            entries to delete
     * @return R-tree with entries deleted up to one matching occurence per entry
     */
    public RTree<T, S> delete(Iterable<Entry<T, S>> entries) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries)
            tree = tree.delete(entry);
        return tree;
    }

    /**
     * If <code>all</code> is false deletes one entry matching the given value and
     * Geometry. If <code>all</code> is true deletes all entries matching the given
     * value and geometry. This method has no effect if the entry is not present.
     * The entry must match on both value and geometry to be deleted.
     * 
     * @param value
     *            the value of the {@link Entry} to be deleted
     * @param geometry
     *            the geometry of the {@link Entry} to be deleted
     * @param all
     *            if false deletes one if exists else deletes all
     * @return a new immutable R-tree without one or many instances of the specified
     *         entry if it exists otherwise returns the original RTree object
     */
    public RTree<T, S> delete(T value, S geometry, boolean all) {
        return delete(context.factory().createEntry(value, geometry), all);
    }

    /**
     * Deletes maximum one entry matching the given value and geometry. This method
     * has no effect if the entry is not present. The entry must match on both value
     * and geometry to be deleted.
     * 
     * @param value
     *            the value to be matched for deletion
     * @param geometry
     *            the geometry to be matched for deletion
     * @return an immutable RTree without one entry (if found) matching the given
     *         value and geometry
     */
    public RTree<T, S> delete(T value, S geometry) {
        return delete(context.factory().createEntry(value, geometry), false);
    }

    /**
     * Deletes one or all matching entries depending on the value of
     * <code>all</code>. If multiple copies of the entry are in the R-tree only one
     * will be deleted if all is false otherwise all matching entries will be
     * deleted. The entry must match on both value and geometry to be deleted.
     * 
     * @param entry
     *            the {@link Entry} to be deleted
     * @param all
     *            if true deletes all matches otherwise deletes first found
     * @return a new immutable R-tree without one instance of the specified entry
     */
    public RTree<T, S> delete(Entry<? extends T, ? extends S> entry, boolean all) {
        if (root.isPresent()) {
            NodeAndEntries<T, S> nodeAndEntries = root.get().delete(entry, all);
            if (nodeAndEntries.node().isPresent() && nodeAndEntries.node().get() == root.get())
                return this;
            else
                return new RTree<T, S>(nodeAndEntries.node(),
                        size - nodeAndEntries.countDeleted() - nodeAndEntries.entriesToAdd().size(), context)
                                .add(nodeAndEntries.entriesToAdd());
        } else
            return this;
    }

    /**
     * Deletes one entry if it exists, returning an immutable copy of the RTree
     * without that entry. If multiple copies of the entry are in the R-tree only
     * one will be deleted. The entry must match on both value and geometry to be
     * deleted.
     * 
     * @param entry
     *            the {@link Entry} to be deleted
     * @return a new immutable R-tree without one instance of the specified entry
     */
    public RTree<T, S> delete(Entry<? extends T, ? extends S> entry) {
        return delete(entry, false);
    }

    public Iterable<Entry<T, S>> search(final Rectangle r, final RunningThreshold maxDistance) {
        if (root.isPresent())
            return Search.search(root.get(), r, maxDistance);
        else
            return Collections.emptyList();
    }


    public Iterable<LeafDefault<T, S>> leafs(){
        List<LeafDefault<T, S>> leafs = new ArrayList<>();
        visit(new Visitor<T, S>() {
            @Override
            public void leaf(Leaf<T, S> leaf) {
                leafs.add((LeafDefault<T, S>) leaf);
            }
            @Override
            public void nonLeaf(NonLeaf<T, S> nonLeaf) {
            }
        });
        return leafs;
    }

    public Optional<? extends Node<T, S>> root() {
        return root;
    }

    /**
     * If the RTree has no entries returns {@link Optional#empty} otherwise returns
     * the minimum bounding rectangle of all entries in the RTree.
     * 
     * @return minimum bounding rectangle of all entries in RTree
     */
    public Optional<Rectangle> mbr() {
        if (!root.isPresent())
            return empty();
        else
            return of(root.get().geometry().mbr());
    }

    /**
     * Returns true if and only if the R-tree is empty of entries.
     * 
     * @return is R-tree empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of entries in the RTree.
     * 
     * @return the number of entries
     */
    public int size() {
        return size;
    }

    /**
     * Returns a {@link Context} containing the configuration of the RTree at the
     * time of instantiation.
     * 
     * @return the configuration of the RTree prior to instantiation
     */
    public Context<T, S> context() {
        return context;
    }

    /**
     * Returns a human readable form of the RTree. Here's an example:
     * 
     * <pre>
     * mbr=Rectangle [x1=10.0, y1=4.0, x2=62.0, y2=85.0]
     *   mbr=Rectangle [x1=28.0, y1=4.0, x2=34.0, y2=85.0]
     *     entry=Entry [value=2, geometry=Point [x=29.0, y=4.0]]
     *     entry=Entry [value=1, geometry=Point [x=28.0, y=19.0]]
     *     entry=Entry [value=4, geometry=Point [x=34.0, y=85.0]]
     *   mbr=Rectangle [x1=10.0, y1=45.0, x2=62.0, y2=63.0]
     *     entry=Entry [value=5, geometry=Point [x=62.0, y=45.0]]
     *     entry=Entry [value=3, geometry=Point [x=10.0, y=63.0]]
     * </pre>
     * 
     * @return a string representation of the RTree
     */
    public String asString() {
        if (!root.isPresent())
            return "";
        else
            return asString(root.get(), "");
    }

    private final static String marginIncrement = "  ";

    private String asString(Node<T, S> node, String margin) {
        StringBuilder s = new StringBuilder();
        s.append(margin);
        s.append("mbr=");
        s.append(node.geometry());
        s.append('\n');
        if (!node.isLeaf()) {
            NonLeaf<T, S> n = (NonLeaf<T, S>) node;
            for (int i = 0; i < n.count(); i++) {
                Node<T, S> child = n.child(i);
                s.append(asString(child, margin + marginIncrement));
            }
        } else {
            Leaf<T, S> leaf = (Leaf<T, S>) node;

            for (Entry<T, S> entry : leaf.entries()) {
                s.append(margin);
                s.append(marginIncrement);
                s.append("entry=");
                s.append(entry);
                s.append('\n');
            }
        }
        return s.toString();
    }

    public int dimensions() {
        return context.dimensions();
    }

    /**
     * Returns all entries in the tree as an {@link Iterable} sequence.
     *
     * @return all entries in the R-tree
     */
    public Iterable<Entry<T, S>> entries() {
        return search(Point.create(new double[dimensions()]), new RunningThreshold(Double.MAX_VALUE));
    }
    
    public void visit(Visitor<T, S> visitor) {
        if (root.isPresent()) {
            visit(root.get(), visitor);
        }
    }

    private void visit(Node<T, S> node, Visitor<T, S> visitor) {
        if (node.isLeaf()) {
            visit((Leaf<T,S>)node, visitor);
        } else {
            visit((NonLeaf<T,S>) node, visitor);
        }
    }
    
    private void visit(Leaf<T, S> leaf, Visitor<T, S> visitor) {
        visitor.leaf(leaf);
    }
    
    private void visit(NonLeaf<T, S> nonLeaf, Visitor<T, S> visitor) {
        visitor.nonLeaf(nonLeaf);
        for (Node<T, S> node: nonLeaf.children()) {
            visit(node, visitor);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(root.orElse(null));
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Node<T, S> rootNode = (Node<T, S>) in.readObject();
        try {
            Field rootField = RTree.class.getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(this, Optional.ofNullable(rootNode));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IOException("Failed to deserialize RTree", e);
        }
    }
    
}
