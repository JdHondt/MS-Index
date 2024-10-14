package net.jelter.algorithms.dstree;

import java.util.ArrayList;

public interface Node {
    boolean isFull();
    void insert(DSSubSequence ss);
    Node convertToInternalNode(DSSubSequence newSequence);
    double minimumDistance(SubSequence query);
    ArrayList<DSSubSequence> getBest(SubSequence query);

    int storageUsed();
}
