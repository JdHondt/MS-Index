/*
 * Copyright 2017 Jiaye Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.algorithms.kvmatch.common;

/**
 * Window interval (WI) for Norm KV-match
 * <p>
 * Created by Jiaye Wu on 16-7-1.
 */
public class NormInterval {

    private int left;

    private int right;

    private double exLower;

    private double ex2Lower;

    private double exUpper;

    private double ex2Upper;

    private long betaPartitions;

    public NormInterval(int left, int right) {
        this.left = left;
        this.right = right;
    }

    public NormInterval(int left, int right, double exLower, double ex2Lower, double exUpper, double ex2Upper, long betaPartitions) {
        this.left = left;
        this.right = right;
        this.exLower = exLower;
        this.ex2Lower = ex2Lower;
        this.exUpper = exUpper;
        this.ex2Upper = ex2Upper;
        this.betaPartitions = betaPartitions;
    }

    public NormInterval(int left, int right, double ex, double ex2, long betaPartitions) {
        this.left = left;
        this.right = right;
        this.exLower = ex;
        this.ex2Lower = ex2;
        this.betaPartitions = betaPartitions;
    }

    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public double getExLower() {
        return exLower;
    }

    public void setExLower(double exLower) {
        this.exLower = exLower;
    }

    public double getEx2Lower() {
        return ex2Lower;
    }

    public void setEx2Lower(double ex2Lower) {
        this.ex2Lower = ex2Lower;
    }

    public double getExUpper() {
        return exUpper;
    }

    public void setExUpper(double exUpper) {
        this.exUpper = exUpper;
    }

    public double getEx2Upper() {
        return ex2Upper;
    }

    public void setEx2Upper(double ex2Upper) {
        this.ex2Upper = ex2Upper;
    }

    public long getBetaPartitions() {
        return betaPartitions;
    }

    public void setBetaPartitions(long betaPartitions) {
        this.betaPartitions = betaPartitions;
    }

    @Override
    public String toString() {
        return "\n[" + left + ", " + right + "] - Ex: " + exLower + "-" + exUpper + ", Ex2: " + ex2Lower + "-" + ex2Upper;
    }
}
