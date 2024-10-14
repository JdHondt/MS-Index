package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;
import java.util.List;

public interface NonLeafFactory<T extends Serializable, S extends Geometry> {

    NonLeaf<T, S> createNonLeaf(List<? extends Node<T, S>> children, Context<T, S> context);
}
