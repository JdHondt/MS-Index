package net.jelter.utils.rtreemulti;

import net.jelter.utils.rtreemulti.geometry.Geometry;
import net.jelter.utils.rtreemulti.geometry.HasGeometry;
import net.jelter.utils.rtreemulti.internal.EntryDefault;
import net.jelter.utils.rtreemulti.internal.Serializer;

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