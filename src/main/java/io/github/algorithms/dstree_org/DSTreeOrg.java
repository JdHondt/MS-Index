package io.github.algorithms.dstree_org;

import lombok.Getter;
import lombok.Setter;
import io.github.algorithms.dstree_org.util.DistUtil;
import io.github.utils.Parameters;
import io.github.algorithms.dstree_org.util.TimeSeries;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

import static io.github.utils.Parameters.*;


public class DSTreeOrg implements Serializable {
    private Node root;
    @Setter
    @Getter
    private int dimension;

    public void buildIndex(TimeSeries[][][] subsequences, int dimension) {
        this.dimension = dimension;
        int threshold = Parameters.indexLeafSize;

        int tsLength = qLen;
        int segmentSize = 4;

        try {
            buildIndex(subsequences, threshold, tsLength, segmentSize);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public File getIndexPath() {
        return Paths.get("index_caches/" + String.join("_",
                algorithmType.toString(),
                datasetName.toString(),
                String.valueOf(N),
                String.valueOf(maxM),
                String.valueOf(seed),
                String.valueOf(qLen),
                String.valueOf(channels),
                normalize ? "1" : "0",
                String.valueOf(fftCoveredDistance),
                String.valueOf(dimension)
        )).toFile();
    }

    public void buildIndex(TimeSeries[][][] timeSeries, int threshold, int tsLength, int segmentSize) throws IOException, ClassNotFoundException {
        root = new Node(threshold, this.dimension);

        // Init helper class instances
        INodeSegmentSplitPolicy[] nodeSegmentSplitPolicies = new INodeSegmentSplitPolicy[2];
        nodeSegmentSplitPolicies[0] = new MeanNodeSegmentSplitPolicy();
        nodeSegmentSplitPolicies[1] = new StdevNodeSegmentSplitPolicy();
        root.setNodeSegmentSplitPolicies(nodeSegmentSplitPolicies);

        MeanStdevSeriesSegmentSketcher seriesSegmentSketcher = new MeanStdevSeriesSegmentSketcher();
        root.setSeriesSegmentSketcher(seriesSegmentSketcher);
        root.setNodeSegmentSketchUpdater(new MeanStdevNodeSegmentSketchUpdater(seriesSegmentSketcher));

        root.setRange(new MeanStdevRange());

        // Calc the split points by segmentSize
        short[] points = calcPoints(tsLength, segmentSize);
        root.initSegments(points);

//        Insert the time series
        for (TimeSeries[][] tss : timeSeries) {
            for (TimeSeries ts : tss[this.dimension]) {
                root.insert(ts);
            }
        }
    }

    public void removeIndex() {
        try {
            FileUtils.deleteDirectory(getIndexPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private short[] calcPoints(int tsLength, int segmentNo) {
        int avgLength = tsLength / segmentNo;
        short[] points = new short[segmentNo];
        for (int i = 0; i < points.length; i++) {
            points[i] = (short) ((i + 1) * avgLength);
        }

        //set the last one
        points[points.length - 1] = (short) tsLength;
        return points;
    }

    public List<TimeSeries> approxKNN(double[] query, int k) {
//        Get node where the query would be located
        Node bsfNode = approximateSearch(query, root);

        try {
            TimeSeries[] approxKNN = DistUtil.minDistBinaryKnn(bsfNode, query, k, this.dimension);
            return Arrays.asList(approxKNN);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Node approximateSearch(double[] queryTs, Node currentNode) {
        if (currentNode.isTerminal()) {
            return currentNode;
        } else //internal node
        {
            if (currentNode.splitPolicy.routeToLeft(queryTs))
                return approximateSearch(queryTs, currentNode.left);
            else
                return approximateSearch(queryTs, currentNode.right);
        }
    }

    public List<TimeSeries> thresholdQuery(double[] query, double threshold) {
//        Initialize the output
        ArrayList<TimeSeries> out = new ArrayList<>();

//        Initialize the queue
        LinkedList<Node> queue = new LinkedList<>();
        queue.add(root);

        Node currentNode;
        while (!queue.isEmpty()) {
            currentNode = queue.poll();

//            If leaf, calculate the distances
            if (currentNode.isTerminal()) {
                ArrayList<TimeSeries> tss = currentNode.getTimeSeriesList();
                out.addAll(tss);
            } else { // internal node, add children that are within the threshold
//              Left
                Node left = currentNode.left;
                if (left.size > 0) {
                    double minDist = DistTools.minDist(left, query);
                    if (minDist < threshold) queue.add(left);
                }

//              Right
                Node right = currentNode.right;
                if (right.size > 0) {
                    double minDist = DistTools.minDist(right, query);
                    if (minDist < threshold) queue.add(right);
                }
            }
        }

        return out;
    }

    public double memoryUsage() {
//        Get the size of the saved index by extracting the size of the serialized file

//        Get the directory
        File dir = getIndexPath();

//        Check if file exists
        if (!dir.exists()) {
            Logger.getGlobal().info("Trying to get memory usage of non-existing index");
            return 0;
        }

//        Get the size of all files in the directory
        long size = FileUtils.sizeOfDirectory(dir);
        return size / 1000000d;
    }
}
