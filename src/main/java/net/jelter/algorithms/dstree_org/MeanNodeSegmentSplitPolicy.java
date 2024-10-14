package net.jelter.algorithms.dstree_org;

/**
 * Created by IntelliJ IDEA.
 * User: wangyang
 * Date: 11-7-10
 * Time: 上午10:30
 * To change this template use File | Settings | File Templates.
 */
public class MeanNodeSegmentSplitPolicy implements INodeSegmentSplitPolicy {
    // 新生成两个子节点的nodeSegmentSketch， 将均值范围对半切分
    public NodeSegmentSketch[] split(NodeSegmentSketch nodeSegmentSketch) {
        float max_mean = nodeSegmentSketch.indicators[0];
        float min_mean = nodeSegmentSketch.indicators[1];
        indicatorSplitValue = (max_mean + min_mean) / 2;  //the mean value is split value

        NodeSegmentSketch[] ret = new NodeSegmentSketch[2]; //split into 2 node
        ret[0] = new NodeSegmentSketch();
        ret[0].indicators = new float[nodeSegmentSketch.indicators.length];
        ret[1] = new NodeSegmentSketch();
        ret[1].indicators = new float[nodeSegmentSketch.indicators.length];
        for (NodeSegmentSketch segmentSketch : ret) {
            System.arraycopy(nodeSegmentSketch.indicators, 0, segmentSketch.indicators, 0, segmentSketch.indicators.length);
        }

        ret[0].indicators[1] = indicatorSplitValue;
        ret[1].indicators[0] = indicatorSplitValue;
        return ret;  //To change body of implemented methods use File | Settings | File Templates.
    }

    int indicatorSplitIdx = 0;
    float indicatorSplitValue;

    public int getIndicatorSplitIdx() {
        return indicatorSplitIdx;
    }

    public float getIndicatorSplitValue() {
        return indicatorSplitValue;
    }
}
