package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;

public interface Visitor<T extends Serializable, S extends Geometry> {

    void leaf(Leaf<T, S> leaf);

    void nonLeaf(NonLeaf<T, S> nonLeaf);

}
