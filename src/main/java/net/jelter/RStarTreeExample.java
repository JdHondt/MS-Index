package net.jelter;

import net.jelter.utils.rtreemulti.RTree;
import net.jelter.utils.rtreemulti.geometry.Point;

import java.util.Random;

public class RStarTreeExample {

    public static void main(String[] args) {
        Random random = new Random(4);
        final int nPoints = 1000;
        final int nClusters = 4;

        RTree<Integer, Point> starTree = RTree.star().dimensions(2).create();
        RTree<Integer, Point> rTree = RTree.dimensions(2).create();

        // Construct four clusters of points
        for (int c = 0; c < nClusters; c++) {
            int maxVal = nPoints - c * nPoints / nClusters + 40;

            for (int i = 0; i < nPoints / nClusters; i++) {
                final int x = random.nextInt(maxVal);
                final int y = random.nextInt(maxVal);
                final Point point = Point.create(x, y);
                rTree = rTree.add(i, point);
                starTree = starTree.add(i, point);
            }
        }

//        starTree.visualize(500, 500).save("rstar.png");
//        rTree.visualize(500, 500).save("rtree.png");
    }

}
