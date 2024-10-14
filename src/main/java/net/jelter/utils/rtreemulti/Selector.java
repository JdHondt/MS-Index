package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;
import java.util.List;

/**
 * The heuristic used on insert to select which node to add an Entry to.
 * 
 */
public interface Selector extends Serializable{

    /**
     * Returns the node from a list of nodes that an object with the given
     * geometry would be added to.
     * 
     * @param <T>
     *            type of value of entry in tree
     * @param <S>
     *            type of geometry of entry in tree
     * @param g
     *            geometry
     * @param nodes
     *            nodes to select from
     * @return one of the given nodes
     */
    <T extends Serializable, S extends Geometry> Node<T, S> select(Geometry g, List<? extends Node<T, S>> nodes);

}
