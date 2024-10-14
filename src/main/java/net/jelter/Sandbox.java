package net.jelter;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Sandbox {

    public static void main(String[] args) {
        double[] data = new double[3000000];
        // Assume the array is filled with data

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream("doubles.bin"))) {
            for (double d : data) {
                dos.writeDouble(d);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
