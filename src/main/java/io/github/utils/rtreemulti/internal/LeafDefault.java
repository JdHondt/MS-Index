package io.github.utils.rtreemulti.internal;

import lombok.Setter;
import io.github.algorithms.msindex.MinimumBoundingRectangle;
import io.github.utils.rtreemulti.Context;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.Leaf;
import io.github.utils.rtreemulti.Node;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;
import java.util.List;

public final class LeafDefault<T extends Serializable, S extends Geometry> implements Leaf<T, S> {

    @Setter private List<Entry<T, S>> entries;
    private final MinimumBoundingRectangle mbr;
    private final Context<T, S> context;

    public LeafDefault(List<Entry<T, S>> entries, Context<T, S> context) {
        this.entries = entries;
        this.context = context;
        this.mbr = Util.mbr(entries);
    }

    @Override
    public Geometry geometry() {
        return mbr;
    }

    @Override
    public List<Entry<T, S>> entries() {
        return entries;
    }

    @Override
    public int count() {
        return entries.size();
    }

    @Override
    public List<Node<T, S>> add(Entry<? extends T, ? extends S> entry) {
        return LeafHelper.add(entry, this);
    }

    @Override
    public NodeAndEntries<T, S> delete(Entry<? extends T, ? extends S> entry, boolean all) {
        return LeafHelper.delete(entry, all, this);
    }

    @Override
    public Context<T, S> context() {
        return context;
    }

    @Override
    public Entry<T, S> entry(int i) {
        return entries.get(i);
    }

    @Override
    public String toString() {
        return "LeafDefault [mbr=" + mbr + ", entries=" + entries + "]";
    }

}
