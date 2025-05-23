package io.github.utils.rtreemulti.internal;

import com.github.davidmoten.guavamini.Objects;
import com.github.davidmoten.guavamini.Preconditions;
import io.github.utils.rtreemulti.Entry;
import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.internal.util.ObjectsHelper;

import java.io.*;

/**
 * An entry in the R-tree which has a spatial representation.
 * 
 * @param <T>
 *            value type
 * @param <S>
 *            geometry type
 */
public final class EntryDefault<T extends Serializable, S extends Geometry> implements Entry<T, S> {
    private final T value;
    private final S geometry;

    /**
     * Constructor.
     * 
     * @param value
     *            the value of the entry
     * @param geometry
     *            the geometry of the value
     */
    public EntryDefault(T value, S geometry) {
        Preconditions.checkNotNull(geometry);
        this.value = value;
        this.geometry = geometry;
    }

    /**
     * Factory method.
     * 
     * @param <T>
     *            type of value
     * @param <S>
     *            type of geometry
     * @param value
     *            object being given a spatial context
     * @param geometry
     *            geometry associated with the value
     * @return entry wrapping value and associated geometry
     */
    public static <T extends Serializable, S extends Geometry> Entry<T, S> entry(T value, S geometry) {
        return new EntryDefault<T, S>(value, geometry);
    }

    /**
     * Returns the value wrapped by this {@link EntryDefault}.
     * 
     * @return the entry value
     */
    @Override
    public T value() {
        return value;
    }

    @Override
    public S geometry() {
        return geometry;
    }

    @Override
    public String toString() {
        String builder = "Entry [value=" + value + ", geometry=" + geometry + "]";
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value, geometry);
    }

    @Override
    public boolean equals(Object obj) {
        @SuppressWarnings("rawtypes")
        EntryDefault other = ObjectsHelper.asClass(obj, EntryDefault.class);
        if (other != null) {
            return Objects.equal(value, other.value)
                    && Objects.equal(geometry, other.geometry);
        } else
            return false;
    }


}
