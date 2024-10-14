package io.github.algorithms.dstree_org;

import io.github.algorithms.dstree_org.util.TimeSeries;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static io.github.utils.Parameters.qLen;

/**
 * Created with IntelliJ IDEA.
 * User: wangyang
 * Date: 13-3-17
 * Time: 下午5:20
 * To change this template use File | Settings | File Templates.
 */
public class FileBuffer implements Comparable {
    public String fileName;
    private final List<TimeSeries> bufferedList = new ArrayList<>();
    private boolean inDisk = false;

    private int diskCount = 0;

    public int getBufferCount() {
        return bufferedList.size();
    }

    public List<TimeSeries> getAllTimeSeries(int dimension) throws IOException {

        if (diskCount > 0) {
            List<TimeSeries> ret = new ArrayList<>(diskCount / 2);
            //load ts from disk;
            fileBufferManager.ioRead++;
            FileInputStream fis = new FileInputStream(fileName);
            BufferedInputStream bis = new BufferedInputStream(fis);
            DataInputStream dis = new DataInputStream(bis);

            for (int i = 0; i < diskCount; i+=2) {
                int tsId = dis.readInt();
                int ssId = dis.readInt();
                ret.add(new TimeSeries(tsId, dimension, ssId));
            }
            dis.close();
            bis.close();
            fis.close();
            ret.addAll(bufferedList);
            return ret;
        }

        return bufferedList;
    }

    public FileBuffer(FileBufferManager fileBufferManager) {
        this.fileBufferManager = fileBufferManager;
    }

    private final FileBufferManager fileBufferManager;

    public void append(TimeSeries timeSeries) {
        bufferedList.add(timeSeries);
        fileBufferManager.addCount(timeSeries.length());
    }

    public void flushBufferToDisk() throws IOException {
        if (getBufferCount() > 0) {
            appendToFile();
        }
    }

    //do append appendToFile
    private void appendToFile() throws IOException {
        FileOutputStream fos = new FileOutputStream(fileName, true);

        BufferedOutputStream bos = new BufferedOutputStream(fos);
        DataOutputStream dos = new DataOutputStream(bos);

        for (TimeSeries ss: bufferedList) {
            dos.writeInt(ss.tsId);
            dos.writeInt(ss.ssId);
        }
        dos.close();
        bos.flush();
        bos.close();

        fileBufferManager.ioWrite++;
        //remove the ts and adjust the fileBufferManager buffer count
        fileBufferManager.removeCount((long) bufferedList.size() * qLen);
        //update diskCount
        diskCount = diskCount + bufferedList.size();
        bufferedList.clear();

        inDisk = true;
    }

    public void deleteFile() {
        if (inDisk) {
            new File(fileName).delete();
            fileBufferManager.ioDelete++;
            diskCount = 0;
            inDisk = false;
        }
        //remove the ts  and adjust the fileBufferManager buffer count
        if (getBufferCount() > 0) {
            fileBufferManager.removeCount((long) bufferedList.size() * qLen);
            //update diskCount
            bufferedList.clear();
        }
    }

    public double p;

    private double priority() {
//        double notUsedTime = (lastTouched * 1.0 - fileBufferManager.getStartTime()) / OneMinuteInMillis;
//        p = notUsedTime + (getBufferCount() * 1) - 1.1 * (Math.abs(fileBufferManager.getThreshold() / 2.0 - getTotalCount()));
        return getBufferCount();// / (getTotalCount() + fileBufferManager.getThreshold()); //100 is adjust factory
    }

    public int compareTo(Object o) {
        //get time not used time in hour
        FileBuffer fileBuffer1 = (FileBuffer) o;
        return -1 * Double.compare(priority(), fileBuffer1.priority());
    }
}
