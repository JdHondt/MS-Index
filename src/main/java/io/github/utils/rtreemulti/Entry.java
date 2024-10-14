package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.geometry.HasGeometry;
import io.github.utils.rtreemulti.internal.EntryDefault;

import java.io.*;

public interface Entry<T extends Serializable, S extends Geometry> extends HasGeometry, Serializable {

    T value();

    @Override
    S geometry();
    
    public static <T extends Serializable, S extends Geometry> Entry<T,S> entry(T object, S geometry) {
        return EntryDefault.entry(object, geometry);
    }

    public static <T extends Serializable, S extends Geometry> void write(Entry<T,S> entry, ObjectOutputStream out) throws IOException {
        out.writeObject(entry.value());
        out.writeObject(entry.geometry());
    }

    public static <T extends Serializable, S extends Geometry> Entry<T,S> read(ObjectInputStream in) throws IOException, ClassNotFoundException {
        T value = (T) in.readObject();
        S geometry = (S) in.readObject();
        return EntryDefault.entry(value, geometry);
    }


}