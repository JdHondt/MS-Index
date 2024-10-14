package io.github.utils.rtreemulti.internal;

import io.github.utils.rtreemulti.RTree;
import io.github.utils.rtreemulti.geometry.Geometry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

public interface Serializer<T extends Serializable, S extends Geometry> {

    void write(RTree<T,S> tree, OutputStream out) throws IOException;
    
    RTree<T,S> read(InputStream in) throws IOException;
}
