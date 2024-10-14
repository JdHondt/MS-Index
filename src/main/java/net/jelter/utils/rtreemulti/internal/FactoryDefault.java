package net.jelter.utils.rtreemulti.internal;

import net.jelter.utils.rtreemulti.*;
import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;
import java.util.List;

public final class FactoryDefault<T extends Serializable, S extends Geometry> implements Factory<T, S> {

    private static class Holder {
        private static final Factory<Serializable, Geometry> INSTANCE = new FactoryDefault<>();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable, S extends Geometry> Factory<T, S> instance() {
        return (Factory<T, S>) Holder.INSTANCE;
    }

    @Override
    public Leaf<T, S> createLeaf(List<Entry<T, S>> entries, Context<T, S> context) {
        return new LeafDefault<T, S>(entries, context);
    }

    @Override
    public NonLeaf<T, S> createNonLeaf(List<? extends Node<T, S>> children, Context<T, S> context) {
        return new NonLeafDefault<T, S>(children, context);
    }

    @Override
    public Entry<T, S> createEntry(T value, S geometry) {
        return Entry.entry(value, geometry);
    }

}
