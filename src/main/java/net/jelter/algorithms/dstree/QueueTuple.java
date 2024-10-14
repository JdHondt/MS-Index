package net.jelter.algorithms.dstree;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QueueTuple {

    public final double distance;
    public final Node node;

    public double distance(){
        return distance;
    }

    public Node node(){
        return node;
    }

    public String toString(){
        return String.format("(%f, %s)", distance, node);
    }
}
