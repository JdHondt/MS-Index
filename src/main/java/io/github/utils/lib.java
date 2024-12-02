package io.github.utils;

import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.utils.Parameters.*;

import static org.apache.commons.math3.util.FastMath.min;
import static org.apache.commons.math3.util.FastMath.max;

public class lib {

    public static <T> Stream<T> getStream(Collection<T> collection) {
        return parallel || indexing ? collection.parallelStream() : collection.stream();
    }

    public static <T> Stream<T> getStream(Iterable<T> collection) {
        return parallel || indexing ?
                getStream(StreamSupport.stream(collection.spliterator(), true)) :
                getStream(StreamSupport.stream(collection.spliterator(), false));
    }

    public static <T> Stream<T> getStream(Stream<T> stream) {
        return parallel || indexing ? stream.parallel() : stream.sequential();
    }

    public static <T> Stream<T> getStream(T[] array) {
        return parallel || indexing ? Arrays.stream(array).parallel() : Arrays.stream(array).sequential();
    }

    public static double std(double SS, double LS, int n) {
        double val = SS / n - pow2(LS / n);
        if (val < 1e-12) { // for floating point errors
            return 0;
        }
        return FastMath.sqrt(val);
    }

    public static double[] fft(double[] input, int nCoeffs){
        double[] out = new double[nCoeffs * 2];
        int n = input.length;

//        Compute the FFT
        for (int k = 0; k < nCoeffs; k++) {
            double realPart = 0;
            double imagPart = 0;

            for (int t = 0; t < n; t++) {
                realPart += input[t] * precomputedAnglesCos[k][t];
                imagPart -= input[t] * precomputedAnglesSin[k][t];
            }

            out[k * 2] = realPart;
            out[k * 2 + 1] = imagPart;
        }

        return out;
    }

    public static double[] flatten(double[][] array) {
        return Arrays.stream(array).flatMapToDouble(Arrays::stream).toArray();
    }

    public static int[] argsort(final double[] a, final boolean ascending) {
        Integer[] indexes = new Integer[a.length];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(final Integer i1, final Integer i2) {
                return (ascending ? 1 : -1) * Double.compare(a[i1], a[i2]);
            }
        });
        return asArray(indexes);
    }

    public static int[] argsort(final int[] a, final boolean ascending) {
        Integer[] indexes = new Integer[a.length];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(final Integer i1, final Integer i2) {
                return (ascending ? 1 : -1) * Integer.compare(a[i1], a[i2]);
            }
        });
        return asArray(indexes);
    }

    public static <T extends Number> int[] asArray(final T... a) {
        int[] b = new int[a.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = a[i].intValue();
        }
        return b;
    }

    public static int argmin(double[] array) {
        int minIndex = 0;
        double minValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] < minValue) {
                minValue = array[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    public static int argmin(int[] array) {
        int minIndex = 0;
        int minValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] < minValue) {
                minValue = array[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    public static int argmax(double[] array) {
        int maxIndex = 0;
        double maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public static int argmax(int[] array) {
        int maxIndex = 0;
        int maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public static double[] minimum(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            c[i] = min(a[i], b[i]);
        }
        return c;
    }

    public static double[][] minimum(double[][] a, double[][] b) {
        double[][] c = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            c[i] = minimum(a[i], b[i]);
        }
        return c;
    }

//    Get the element-wise minimum of a 2D array
    public static double[] minimum(double[][] a) {
        if (a.length == 1){
            return a[0].clone();
        }

        double[] c = a[0].clone();
        for (int i = 1; i < a.length; i++) {
            c = minimum(c, a[i]);
        }
        return c;
    }

//    Get the element-wise minimum of a 3D array
    public static double[][] minimum(double[][][] a) {
        if (a.length == 1){
            return a[0].clone();
        }

        double[][] c = a[0].clone();
        for (int i = 1; i < a.length; i++) {
            c = minimum(c, a[i]);
        }
        return c;
    }


    public static double[] maximum(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            c[i] = max(a[i], b[i]);
        }
        return c;
    }

    public static double[][] maximum(double[][] a, double[][] b) {
        double[][] c = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            c[i] = maximum(a[i], b[i]);
        }
        return c;
    }

//    Get the element-wise maximum of a 2D array
    public static double[] maximum(double[][] a) {
        if (a.length == 1){
            return a[0].clone();
        }

        double[] c = a[0].clone();
        for (int i = 1; i < a.length; i++) {
            c = maximum(c, a[i]);
        }
        return c;
    }

//    Get the element-wise maximum of a 3D array
    public static double[][] maximum(double[][][] a) {
        if (a.length == 1){
            return a[0].clone();
        }

        double[][] c = a[0].clone();
        for (int i = 1; i < a.length; i++) {
            c = maximum(c, a[i]);
        }
        return c;
    }

    public static double variance(double[] values) {
        double sum = 0;
        double sumSquare = 0;
        for (double value : values) {
            sum += value;
            sumSquare += value * value;
        }
        double avg = sum / values.length;
        double var = sumSquare / values.length - avg * avg;
        return var;
    }

//    Get the row-wise variance of a 2D array
    public static double[] variance(double[][] values){
        double[] var = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            var[i] = variance(values[i]);
        }
        return var;
    }

    public static double pow2(double x) {
        return x * x;
    }

    public static double[] minArray(int n) {
        double[] array = new double[n];
        Arrays.fill(array, Double.POSITIVE_INFINITY);
        return array;
    }

    public static double[] maxArray(int n) {
        double[] array = new double[n];
        Arrays.fill(array, Double.NEGATIVE_INFINITY);
        return array;
    }

    public static double[] znorm(double[] v) {
        double[] z = v.clone();
        double LS = 0;
        double SS = 0;
        for (double value : z) {
            LS += value;
            SS += value * value;
        }
        final double avg = LS / v.length;
        final double std = std(SS, LS, v.length);

        for (int i = 0; i < z.length; i++) {
            z[i] = (z[i] - avg) / std;
            if (Double.isNaN(z[i])) {
                System.out.println("debug: NaN result of znorm");
            }
        }
        return z;
    }

    public static double prod(double[] in) {
        double out = 1;
        for (double v : in) {
            out *= v;
        }
        return out;
    }

    public static int prod(int[] in) {
        int out = 1;
        for (int v : in) {
            out *= v;
        }
        return out;
    }

    public static double[] reverseAndPadd(double[] array, int n) {
        final double[] reversed = new double[n];
        for (int i = 0; i < array.length; i++) {
            reversed[array.length - i - 1] = array[i];
        }
        return reversed;
    }

    public static double euclideanSquaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    public static double minMaxNormalize(double val, double min, double max) {
        final double maxMinDiff = max - min;
        if (maxMinDiff == 0) return 0;
        return (val - min) / maxMinDiff;
    }


    /**
     * Compute the log base 2 of a number.
     *
     * @param N: The number to find the log base 2 for. Note that N must be a power of 2.
     * @return : Log base 2 of N.
     */
    public static int log2(int N) {
        return 31 - Integer.numberOfLeadingZeros(N);
    }

    public static int nextPowerOfTwo(int value) {
        int highestOneBit = Integer.highestOneBit(value);
        if (value == highestOneBit) {
            return value;
        }
        return highestOneBit << 1;
    }

    /**
     * Write array to a file in CSV format.
     */
    public static void writeArrayToFile(double[][] array, String filename) {
        StringBuilder sb = new StringBuilder();
        for (double[] row : array) {
            for (double v : row) {
                sb.append(v).append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("\n");
        }
        writeToFile(filename, sb.toString());
    }

    /**
     * Write array to a file in CSV format.
     */
    public static void writeArrayToFile(double[] array, String filename) {
        StringBuilder sb = new StringBuilder();
        for (double v : array) {
            sb.append(v).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        writeToFile(filename, sb.toString());
    }

    public static void writeToFile(String filename, String content) {
        try {
            Files.write(Paths.get(filename), content.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
