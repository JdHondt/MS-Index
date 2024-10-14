package io.github.algorithms.dstree_org;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: wangyang
 * Date: 13-3-17
 * Time: 下午5:14
 * To change this template use File | Settings | File Templates.
 */
public class FileBufferManager {
    private long maxBufferedSize = 1000 * 1000 * 100; //
    @Setter
    @Getter
    private int threshold;   //use to calc the priority  of remove
    int tsLength;
    int ioWrite = 0;
    int ioRead = 0;
    int ioDelete = 0;

    private long currentCount = 0;


    public void addCount(long count) {
        currentCount += count;
    }

    public void removeCount(long count) {
        currentCount -= count;
    }

    protected FileBuffer createFileBuffer() {
        return new FileBuffer(this);
    }

    public void setBufferedMemorySize(double bufferedMemorySize) {
        maxBufferedSize = Math.round(bufferedMemorySize * 1024 * 1024 / 8);
        batchRemoveSize = maxBufferedSize / 2;
    }

    public long batchRemoveSize = maxBufferedSize / 100;   //batch remove 1/100

    HashMap<String, FileBuffer> fileMap = new HashMap<String, FileBuffer>();

    public FileBuffer getFileBuffer(String fileName) throws IOException {
        FileBuffer fileBuffer = fileMap.get(fileName);
        if (fileBuffer == null) {
            //do LRU remove
            if (currentCount >= maxBufferedSize) {
                long toSize = maxBufferedSize - batchRemoveSize;
                ArrayList<FileBuffer> list = new ArrayList<>(fileMap.values());

//                long validStartTime = System.currentTimeMillis();
//                for (int i = 0; i < list.size(); i++) {
//                    FileBuffer fileBuffer = list.get(i);
//                    if (fileBuffer.getBufferCount() > 0 && fileBuffer.lastTouched < validStartTime)
//                        validStartTime = fileBuffer.lastTouched;
//                }
//
//                System.out.println("startTime = " + startTime);
//                startTime = validStartTime;
//                System.out.println("validStartTime = " + validStartTime);

                Collections.sort(list);
                int idx = 0;
                while (currentCount > toSize) {
                    FileBuffer buffer = list.get(idx);
                    flushBufferToDisk(buffer.fileName);
                    idx++;
                }
            }

            fileBuffer = createFileBuffer();
            fileBuffer.fileName = fileName;
            fileMap.put(fileName, fileBuffer);
        }
        return fileBuffer;

    }

    public void saveAllToDisk() throws IOException {
        Set<Map.Entry<String, FileBuffer>> entries = fileMap.entrySet();
        for (Map.Entry<String, FileBuffer> next : entries) {
            next.getValue().flushBufferToDisk();
        }
    }

    public static FileBufferManager[] fileBufferManager;

    public static FileBufferManager getInstance(int dimension) {
        if (fileBufferManager[dimension] == null) {
            fileBufferManager[dimension] = new FileBufferManager();
        }
        return fileBufferManager[dimension];
    }

    public void flushBufferToDisk(String fileName) throws IOException {
        FileBuffer fileBuffer = fileMap.get(fileName);
        fileBuffer.flushBufferToDisk();
    }

    public void DeleteFile(String fileName) {
        //call when do split
        FileBuffer fileBuffer = fileMap.get(fileName);
        fileBuffer.deleteFile();
        //delete file is never used anymore
        fileMap.remove(fileName);
    }

//    public
}
