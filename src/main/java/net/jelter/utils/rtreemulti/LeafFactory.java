package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;
import java.util.List;

public interface LeafFactory<T extends Serializable, S extends Geometry> {
    Leaf<T, S> createLeaf(List<Entry<T, S>> entries, Context<T, S> context);
}
