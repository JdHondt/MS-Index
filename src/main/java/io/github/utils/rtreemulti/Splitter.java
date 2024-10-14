package io.github.utils.rtreemulti;

import io.github.utils.rtreemulti.geometry.HasGeometry;
import io.github.utils.rtreemulti.geometry.ListPair;

import java.io.*;
import java.util.List;

public interface Splitter extends Serializable {

    /**
     * Splits a list of items into two lists of at least minSize.
     * 
     * @param <T>
     *            geometry type
     * @param items
     *            list of items to split
     * @param minSize
     *            min size of each list
     * @return two lists
     */
    <T extends HasGeometry> ListPair<T> split(List<T> items, int minSize);

    static Splitter read(InputStream input) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(input)) {
            return (Splitter) ois.readObject();
        }
    }

    static void write(Splitter splitter, OutputStream output) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(output)) {
            oos.writeObject(splitter);
        }
    }

}
