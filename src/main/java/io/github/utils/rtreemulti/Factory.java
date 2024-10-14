package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.internal.FactoryDefault;

import java.io.Serializable;

public interface Factory<T extends Serializable, S extends Geometry>
        extends LeafFactory<T, S>, NonLeafFactory<T, S>, EntryFactory<T,S>, Serializable {
    
    public static <T extends Serializable, S extends Geometry> Factory<T, S> defaultFactory() {
        return FactoryDefault.instance();
    }
}
