package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;
import net.jelter.utils.rtreemulti.internal.Comparators;

import java.io.Serializable;
import java.util.List;

import static java.util.Collections.min;

/**
 * Uses minimal volume increase to select a node from a list.
 *
 */
public final class SelectorMinimalVolumeIncrease implements Selector {

    public static final SelectorMinimalVolumeIncrease INSTANCE = new SelectorMinimalVolumeIncrease();

    private SelectorMinimalVolumeIncrease() {
    }

    @Override
    public <T extends Serializable, S extends Geometry> Node<T, S> select(Geometry g, List<? extends Node<T, S>> nodes) {
        return min(nodes, Comparators.volumeIncreaseThenVolumeComparator(g.mbr()));
    }
}
