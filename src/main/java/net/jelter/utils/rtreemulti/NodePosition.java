package net.jelter.utils.rtreemulti;

import com.github.davidmoten.guavamini.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.jelter.utils.rtreemulti.geometry.Geometry;

import java.io.Serializable;

//Mutable, not thread-safe
@RequiredArgsConstructor
final class NodePosition<T extends Serializable, S extends Geometry> {

    @NonNull private Node<T, S> node;
    @NonNull private int position;
    @NonNull @Getter
    private double priority;


    Node<T, S> node() {
        return node;
    }

    int position() {
        return position;
    }

    boolean hasRemaining() {
        return position != node.count();
    }

    void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "NodePosition [position=" + position + ", priority=" + priority + "]";
    }

}
