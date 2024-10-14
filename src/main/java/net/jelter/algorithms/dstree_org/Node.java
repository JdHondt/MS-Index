package net.jelter.algorithms.dstree_org;

import de.ruedigermoeller.serialization.FSTObjectInput;
import de.ruedigermoeller.serialization.FSTObjectOutput;
import lombok.Getter;
import lombok.Setter;
import net.jelter.algorithms.dstree_org.util.CalcUtil;
import net.jelter.algorithms.dstree_org.util.TimeSeries;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: wangyang
 * Date: 11-4-27
 * Time: 下午9:07
 * To change this template use File | Settings | File Templates.
 */
public class Node implements Serializable {

    private int dimension;

    @Setter
    transient INodeSegmentSplitPolicy[] nodeSegmentSplitPolicies;

    @Setter
    transient IRange range;

    SplitPolicy splitPolicy;
    short[] nodePoints;
    transient short[] hsNodePoints;
    NodeSegmentSketch[] nodeSegmentSketches;
    transient NodeSegmentSketch[] hsNodeSegmentSketches; //for horizontal splitting

    @Setter
    @Getter
    transient ISeriesSegmentSketcher seriesSegmentSketcher; //= new MeanStdevSeriesSegmentSketcher();
    @Setter
    transient INodeSegmentSketchUpdater nodeSegmentSketchUpdater;// = new MeanStdevNodeSegmentSketchUpdater(seriesSegmentSketcher);

    Node parent;

    //start from 0
    int level = 0;

    boolean isLeft;

    transient int threshold;
    @Getter
    int size = 0;

    Node left;
    Node right;

    @Getter
    ArrayList<TimeSeries> timeSeriesList = new ArrayList<>();

    public static int maxSegmentLength = 2;
    public static int maxValueLength = 15;

    public Node(Node parent, int dimension) {
        this(parent.threshold, dimension);
        this.dimension = dimension;
        this.nodeSegmentSplitPolicies = parent.nodeSegmentSplitPolicies;
        this.range = parent.range;
        this.nodeSegmentSketchUpdater = parent.nodeSegmentSketchUpdater;
        this.seriesSegmentSketcher = parent.seriesSegmentSketcher;
        this.parent = parent;

        level = parent.level + 1;
    }

    public Node(int threshold) {
        this.threshold = threshold;
    }

    public Node(int threshold, int dimension) {
        this.threshold = threshold;
        this.dimension = dimension;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public int getSegmentSize() {
        return nodePoints.length;
    }

    public short getSegmentStart(short[] points, int idx) {
        if (idx == 0)
            return 0;
        else
            return points[idx - 1];
    }

    public short getSegmentEnd(short[] points, int idx) {
        return points[idx];
    }

    public int getSegmentLength(int i) {
        if (i == 0)
            return nodePoints[i];
        else
            return nodePoints[i] - nodePoints[i - 1];
    }

    public int getSegmentLength(short[] points, int i) {
        if (i == 0)
            return points[i];
        else
            return points[i] - points[i - 1];
    }

    public int countNodes() {
        int count = 1;
        if (left != null) count += left.countNodes();
        if (right != null) count += right.countNodes();
        return count;
    }

    // is leaf node
    public boolean isTerminal() {
        return (left == null && right == null);
    }

    public void append(TimeSeries timeSeries) throws IOException {
        timeSeriesList.add(timeSeries);
    }

    public static double hsTradeOffFactor = 2;

    @Getter
    List<double[]> tss = new ArrayList<>();

    public void insert(TimeSeries timeSeries) throws IOException {
        //update statistics dynamically for leaf and branch
        updateStatistics(timeSeries.getTs());

        if (isTerminal()) {
            append(timeSeries);            //append to file first
            if (threshold == size) {   //do split
                splitPolicy = new SplitPolicy();
                splitPolicy.setSeriesSegmentSketcher(this.getSeriesSegmentSketcher());

                //init the vars used in loop
                // max DiffValue is the BSF answer for target function to assess the split strategy
                double maxDiffValue = Double.MAX_VALUE * -1;
                double avg_children_range_value = 0;
                short verticalSplitPoint = -1; //default not do vertical split

                //we want to test every horizontal split policy for each segment
                for (int i = 0; i < nodePoints.length; i++) {
                    //for each segment
                    // QoS of original node
                    double nodeRangeValue = range.calc(nodeSegmentSketches[i], getSegmentLength(nodePoints, i));

                    //for every split policy
                    for (INodeSegmentSplitPolicy nodeSegmentSplitPolicy : nodeSegmentSplitPolicies) {
                        NodeSegmentSketch[] childNodeSegmentSketches = nodeSegmentSplitPolicy.split(nodeSegmentSketches[i]);

                        // usually the length is 2, for the binary split
                        double[] rangeValues = new double[childNodeSegmentSketches.length];
                        // QoS of every children nodes, calc the avg QoS
                        for (int k = 0; k < childNodeSegmentSketches.length; k++) {
                            NodeSegmentSketch childNodeSegmentSketch = childNodeSegmentSketches[k];
                            rangeValues[k] = range.calc(childNodeSegmentSketch, getSegmentLength(nodePoints, i));
                        }

                        avg_children_range_value = CalcUtil.avg(rangeValues);

                        double diffValue = nodeRangeValue - avg_children_range_value;
//                        if (nodeSegmentSplitPolicy instanceof  SlopeNodeSegmentSplitPolicy)
//                            diffValue = diffValue * 2;
                        if (diffValue > maxDiffValue) {
                            maxDiffValue = diffValue;
                            splitPolicy.splitFrom = getSegmentStart(nodePoints, i);
                            splitPolicy.splitTo = getSegmentEnd(nodePoints, i);
                            splitPolicy.indicatorIdx = nodeSegmentSplitPolicy.getIndicatorSplitIdx();
                            splitPolicy.indicatorSplitValue = nodeSegmentSplitPolicy.getIndicatorSplitValue();
                            splitPolicy.setNodeSegmentSplitPolicy(nodeSegmentSplitPolicy);
                        }
                    }
                }

//                System.out.println("before maxDiffValue = " + maxDiffValue);

                //wy add trade off for horizontal split; bias for horizontal split for no need to copy series data
                maxDiffValue = maxDiffValue * hsTradeOffFactor;

                //for every hsNodeSegmentSketches
                // hsNodeSegmentSketches is a fault naming, which should be vertical split node segment sketches
                for (int i = 0; i < hsNodePoints.length; i++) {
                    //for each segment
                    double nodeRangeValue = range.calc(hsNodeSegmentSketches[i], getSegmentLength(hsNodePoints, i));

                    //for every split policy
                    for (INodeSegmentSplitPolicy hsNodeSegmentSplitPolicy : nodeSegmentSplitPolicies) {
                        NodeSegmentSketch[] childNodeSegmentSketches = hsNodeSegmentSplitPolicy.split(hsNodeSegmentSketches[i]);

                        double[] rangeValues = new double[childNodeSegmentSketches.length];
                        for (int k = 0; k < childNodeSegmentSketches.length; k++) {
                            NodeSegmentSketch childNodeSegmentSketch = childNodeSegmentSketches[k];
                            rangeValues[k] = range.calc(childNodeSegmentSketch, getSegmentLength(hsNodePoints, i));
                        }

                        avg_children_range_value = CalcUtil.avg(rangeValues);

                        double diffValue = nodeRangeValue - avg_children_range_value;
//                        if (hsNodeSegmentSplitPolicy instanceof  SlopeNodeSegmentSplitPolicy)
//                            diffValue = diffValue * 2;

                        if (diffValue > maxDiffValue) {
//                            System.out.println("diffValue = " + diffValue);
                            maxDiffValue = diffValue;
                            splitPolicy.splitFrom = getSegmentStart(hsNodePoints, i);
                            splitPolicy.splitTo = getSegmentEnd(hsNodePoints, i);
                            splitPolicy.indicatorIdx = hsNodeSegmentSplitPolicy.getIndicatorSplitIdx();
                            splitPolicy.indicatorSplitValue = hsNodeSegmentSplitPolicy.getIndicatorSplitValue();
                            splitPolicy.setNodeSegmentSplitPolicy(hsNodeSegmentSplitPolicy);

                            //get horizontalSplitPoint
                            verticalSplitPoint = getHorizontalSplitPoint(nodePoints, splitPolicy.splitFrom, splitPolicy.splitTo);
                        }
                    }
                }

//                System.out.println("horizontalSplitPoint = " + horizontalSplitPoint);
//                System.out.println("splitPolicy.getVerticalSplitPolicy().getClass().getSimpleName() = " + splitPolicy.getNodeSegmentSplitPolicy().getClass().getSimpleName());
                // add by zeyu, for duplicate dataset
                if (maxDiffValue == Double.NEGATIVE_INFINITY) return;


                short[] childNodePoint;
                if (verticalSplitPoint < 0) //not vs
                {
                    childNodePoint = new short[nodePoints.length];
                    System.arraycopy(nodePoints, 0, childNodePoint, 0, nodePoints.length);
                } else {
                    childNodePoint = new short[nodePoints.length + 1];
                    System.arraycopy(nodePoints, 0, childNodePoint, 0, nodePoints.length);
                    childNodePoint[childNodePoint.length - 1] = verticalSplitPoint;
                    Arrays.sort(childNodePoint);
                }

                //init children node
                left = new Node(this, dimension);
                left.initSegments(childNodePoint);
                left.isLeft = true;

                right = new Node(this, dimension);
                right.initSegments(childNodePoint);
                right.isLeft = false;

                for (TimeSeries ss : timeSeriesList) {
                    if (splitPolicy.routeToLeft(ss.getTs()))
                        left.insert(ss);
                    else
                        right.insert(ss);
                }
            }
        } else { //not terminal
            if (splitPolicy.routeToLeft(timeSeries.getTs()))
                left.insert(timeSeries);
            else
                right.insert(timeSeries);
        }
    }

    short getHorizontalSplitPoint(short[] points, short from, short to) {
        if (Arrays.binarySearch(points, to) < 0) {
            return to;
        } else
            return from;
    }



    // 更新该节点中每个segment的均值、方差的极值，其中某个节点才会表示该节点的范围
    private void updateStatistics(double[] timeSeries) {
        size++;

        //update nodeSegmentSketches
        for (int i = 0; i < nodePoints.length; i++) {
            NodeSegmentSketch nodeSegmentSketch = nodeSegmentSketches[i];
            nodeSegmentSketchUpdater.updateSketch(nodeSegmentSketch, timeSeries, getSegmentStart(nodePoints, i), getSegmentEnd(nodePoints, i));
        }

        //update hsNodeSegmentSketches
        for (int i = 0; i < hsNodePoints.length; i++) {
            NodeSegmentSketch hsNodeSegmentSketch = hsNodeSegmentSketches[i];
            nodeSegmentSketchUpdater.updateSketch(hsNodeSegmentSketch, timeSeries, getSegmentStart(hsNodePoints, i), getSegmentEnd(hsNodePoints, i));
        }
    }



    public String formatInt(int value, int length) {
        StringBuilder ret = new StringBuilder(String.valueOf(value));
        if (ret.length() > length) {
            throw new RuntimeException("exceed length:" + length);
        }
        while (ret.length() < length) {
            ret.insert(0, "0");
        }
        return ret.toString();
    }

    public String formatDouble(double value, int length) {
        String ret = String.valueOf(value);
        if (ret.length() > length) {
            ret = ret.substring(0, length - 1);
        }
        return ret;
    }

    public void initSegments(short[] segmentPoints) {
        this.nodePoints = new short[segmentPoints.length];
        System.arraycopy(segmentPoints, 0, this.nodePoints, 0, segmentPoints.length);

        this.hsNodePoints = CalcUtil.split(segmentPoints, (short) 1); //min length is 1

        //init nodeSegmentSketches and hsNodeSegmentSketches
        nodeSegmentSketches = new NodeSegmentSketch[nodePoints.length];
        for (int i = 0; i < nodeSegmentSketches.length; i++) {
            nodeSegmentSketches[i] = new NodeSegmentSketch();
        }

        hsNodeSegmentSketches = new NodeSegmentSketch[hsNodePoints.length];
        for (int i = 0; i < hsNodeSegmentSketches.length; i++) {
            hsNodeSegmentSketches[i] = new NodeSegmentSketch();
        }
    }

    public void saveToFile(String fileName) throws IOException {
        FSTObjectOutput out = new FSTObjectOutput(new FileOutputStream(fileName));
        out.writeObject(this);
        out.close(); // required !
    }

    public static Node loadFromFile(String fileName) throws IOException, ClassNotFoundException {
        FSTObjectInput in = new FSTObjectInput(new FileInputStream(fileName));
        Node node;
        try {
            node = (Node) in.readObject(Node.class);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        in.close();
        return node;
    }
}
