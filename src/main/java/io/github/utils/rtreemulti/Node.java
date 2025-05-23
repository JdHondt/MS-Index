package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.geometry.HasGeometry;
import io.github.utils.rtreemulti.internal.NodeAndEntries;

import java.io.Serializable;
import java.util.List;

public interface Node<T extends Serializable, S extends Geometry> extends HasGeometry, Serializable {

    List<Node<T, S>> add(Entry<? extends T, ? extends S> entry);

    NodeAndEntries<T, S> delete(Entry<? extends T, ? extends S> entry, boolean all);

    int count();

    Context<T, S> context();
    
    boolean isLeaf();

}
