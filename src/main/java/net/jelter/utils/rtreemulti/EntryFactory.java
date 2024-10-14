package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;

public interface EntryFactory<T extends Serializable, S extends Geometry> {
    Entry<T,S> createEntry(T value, S geometry);
}
