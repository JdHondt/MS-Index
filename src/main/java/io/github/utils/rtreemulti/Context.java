package io.github.utils.rtreemulti;

import com.github.davidmoten.guavamini.Preconditions;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;

/**
 * Configures an RTree prior to instantiation of an {@link RTree}.
 * @param <T> value type
 * @param <S> geometry type
 */
public final class Context<T extends Serializable, S extends Geometry> implements Serializable {

    private final int dimensions;
    private final int maxChildren;
    private final int minChildren;
    private final int leafSize;
    private final Splitter splitter;
    private final Selector selector;
    private final Factory<T, S> factory;

    /**
     * Constructor.
     * 
     * @param minChildren
     *            minimum number of children per node (at least 1)
     * @param maxChildren
     *            max number of children per node (minimum is 3)
     * @param selector
     *            algorithm to select search path
     * @param splitter
     *            algorithm to split the children across two new nodes
     * @param factory
     *            node creation factory
     */
    public Context(int dimensions, int minChildren, int maxChildren, int leafSize, Selector selector, Splitter splitter,
            Factory<T, S> factory) {
        Preconditions.checkNotNull(splitter);
        Preconditions.checkNotNull(selector);
        Preconditions.checkArgument(maxChildren > 2, "maxChildren must be greater than 2");
        Preconditions.checkArgument(minChildren >= 1, "minChildren must be greater than 0");
        Preconditions.checkArgument(minChildren < maxChildren, "minChildren must be less than maxChildren");
        Preconditions.checkArgument(leafSize >= 1, "leafSize must be greater than 0");
        Preconditions.checkNotNull(factory);
        Preconditions.checkArgument(dimensions > 1, "dimensions must be greater than 1");
        this.dimensions = dimensions;
        this.selector = selector;
        this.maxChildren = maxChildren;
        this.minChildren = minChildren;
        this.leafSize = leafSize;
        this.splitter = splitter;
        this.factory = factory;
    }

    public int maxChildren() {
        return maxChildren;
    }

    public int minChildren() {
        return minChildren;
    }

    public int leafSize() {
        return leafSize;
    }

    public Splitter splitter() {
        return splitter;
    }

    public Selector selector() {
        return selector;
    }

    public Factory<T, S> factory() {
        return factory;
    }

    public int dimensions() {
        return dimensions;
    }

}
