package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.internal.Comparators;

import java.io.Serializable;
import java.util.List;

import static java.util.Collections.min;

public final class SelectorMinimalOverlapVolume implements Selector {
    
    public static final SelectorMinimalOverlapVolume INSTANCE = new SelectorMinimalOverlapVolume();
    
    private SelectorMinimalOverlapVolume() {
    }

    @Override
    public <T extends Serializable, S extends Geometry> Node<T, S> select(Geometry g, List<? extends Node<T, S>> nodes) {
        return min(nodes,
                Comparators.overlapVolumeThenVolumeIncreaseThenVolumeComparator(g.mbr(), nodes));
    }

}
