package net.jelter.utils.rtreemulti.internal;

import net.jelter.utils.rtreemulti.RTree;
import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

public interface Serializer<T extends Serializable, S extends Geometry> {

    void write(RTree<T,S> tree, OutputStream out) throws IOException;
    
    RTree<T,S> read(InputStream in) throws IOException;
}
