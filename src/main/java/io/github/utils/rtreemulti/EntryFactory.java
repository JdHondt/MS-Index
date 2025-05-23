package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;

public interface EntryFactory<T extends Serializable, S extends Geometry> {
    Entry<T,S> createEntry(T value, S geometry);
}
