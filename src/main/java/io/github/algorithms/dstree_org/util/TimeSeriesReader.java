package io.github.algorithms.dstree_org.util;

import lombok.Getter;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: wangyang
 * Date: 10-12-30
 * Time: 下午3:17
 * To change this template use File | Settings | File Templates.
 */
public class TimeSeriesReader {
    BufferedReader bfr;
    @Getter
    String fileName;

    public TimeSeriesReader(String fileName) {
        this.fileName = fileName;
    }

    String line;

    public boolean hasNext() throws IOException {
        line = bfr.readLine();
        return (line != null && !line.trim().isEmpty());
    }

    static String SEPARATOR = "[ \t\n,]";

    public static double[] readFromString(String str) {
        String[] strings = str.split(SEPARATOR);

        double[] ret = new double[strings.length];

        for (int i = 0; i < ret.length; i++) {
            String s = strings[i];
            if (!s.isEmpty())
                ret[i] = Double.parseDouble(s);
        }
        return ret;
    }

//    public double[] getFromClassifierString() throws IOException {
//        line = bfr.readLine();
//        if (line == null)
//            return null;
//        boolean flag = false;
//        double[] ret = null;
//        while (line != null && !flag) {
//            String[] strings = line.split(SEPARATOR);
//            ret = new double[strings.length - 1];
//            try {
//                for (int i = 0; i < ret.length; i++) {
//                    String s = strings[i];
//                    if (s.length() > 0)
//                        ret[i] = Double.parseDouble(s);
//                }
//                flag = true;
//            } catch (NumberFormatException e) {
//                line = bfr.readLine();
//                if (line == null) return null;
//            }
//        }
//    }

    public double[] next() {
        return readFromString(line);
    }

    public void open() throws FileNotFoundException {
        bfr = new BufferedReader(new FileReader(new File(fileName)));
    }

    public void close() throws IOException {
        bfr.close();
    }


}