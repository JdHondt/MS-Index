package io.github.algorithms.msindex;

import lombok.Getter;
import lombok.Setter;
import io.github.utils.DistanceBound;
import io.github.utils.LandmarkMBR;
import io.github.utils.lib;
import io.github.utils.rtreemulti.geometry.Geometry;
import io.github.utils.rtreemulti.geometry.Point;
import io.github.utils.rtreemulti.geometry.Rectangle;
import io.github.utils.rtreemulti.geometry.internal.GeometryUtil;
import io.github.utils.rtreemulti.internal.util.ObjectsHelper;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

import static io.github.utils.Parameters.*;
import static io.github.utils.lib.pow2;

public class MinimumBoundingRectangle implements Rectangle {
    private final double[] mins;
    private final double[] maxes;
    private double[] mids;
    @Setter @Getter private LandmarkMBR landmarkMBR = null;

    public Double[] distanceCache;

    public MinimumBoundingRectangle(double[] mins, double[] maxes) {
        this.mins = mins;
        this.maxes = maxes;
    }

    public MinimumBoundingRectangle(double[] mins, double[] maxes, LandmarkMBR landmarkMBR) {
        this.mins = mins;
        this.maxes = maxes;
        this.landmarkMBR = landmarkMBR;
    }

    public static MinimumBoundingRectangle create(double[] mins, double[] maxes) {
        return new MinimumBoundingRectangle(mins, maxes);
    }

    public static MinimumBoundingRectangle create(double[] mins, double[] maxes, LandmarkMBR landmarkMBR) {
        return new MinimumBoundingRectangle(mins, maxes, landmarkMBR);
    }

    public MinimumBoundingRectangle add(Rectangle r) {
        final double[] newMins = lib.minimum(this.mins, r.mins());
        final double[] newMaxs = lib.maximum(this.maxes, r.maxes());

        return new MinimumBoundingRectangle(newMins, newMaxs);
    }

    public boolean contains(double... p) {
        for (int i = 0; i < p.length; ++i) {
            if (p[i] < this.mins[i] || p[i] > this.maxes[i]) {
                return false;
            }
        }

        return true;
    }

    public boolean intersects(Rectangle r) {
        final int doublesPerDimension = fourierLengthPerDimension();

        for (int d : selectedVariatesIdx) {
            int offset = d * doublesPerDimension;
            for (int i = offset; i < offset + doublesPerDimension; ++i) {
                if (mins[i] > r.maxes()[i] || maxes[i] < r.mins()[i]) {
                    return false;
                }
            }
        }
        return true;
    }

    public DistanceBound landmarkBound(MinimumBoundingRectangle mbr) {
        if (landmarkMBR == null) {
            return null;
        }

        return landmarkMBR.getDistance(mbr.getLandmarkMBR());
    }

    public DistanceBound dftBound(MinimumBoundingRectangle mbr) {
        final int doublesPerDimension = fourierLengthPerDimension();

        final double[] a = mbr.mins();
        final double[] b = mbr.maxes();

        double LB = 0.0;
        double UB = 0.0;

        for (int d : selectedVariatesIdx){
            int offset = d * doublesPerDimension;
            for (int i = offset; i < offset + doublesPerDimension; ++i) {
                // Compute lower bound
                final double ay = a[i] - this.maxes[i];
                final double bx = b[i] - this.mins[i];
                final double ayAbs = FastMath.abs(ay);
                final double bxAbs = FastMath.abs(bx);

                final double minDiff;
                if (!(ay > 0 || bx < 0)) { // Intersection
                    minDiff = 0.0;
                } else {
                    minDiff = pow2(FastMath.min(ayAbs, bxAbs));
                }

                // Compute upper bound
                final double maxDiff = pow2(FastMath.max(ayAbs, bxAbs));

                LB += minDiff;
                UB += maxDiff;

                // Due to symmetry, count all but the first coefficient twice
                int coefId = i % doublesPerDimension / 2;
                if (fftCoefficients[coefId] != 0) {
                    LB += minDiff;
                    UB += maxDiff;
                }
            }
        }

        return new DistanceBound(LB / qLen, UB / qLen); // Frequency domain correction
    }

    public double distance(Rectangle r) {
        //        Check cache
        if (distanceCache == null) {
            distanceCache = new Double[nQueries];
        }
        Double cached = distanceCache[currQueryId];
        if (cached != null) {
            return cached;
        }

        nBoundComputations.getAndIncrement();

        if (r instanceof MinimumBoundingRectangle){
            MinimumBoundingRectangle mbr = (MinimumBoundingRectangle) r;

            final double dftLB = dftBound(mbr).LB();
            double LB = dftLB;

            DistanceBound landmarkLB = landmarkBound(mbr);
            if (landmarkLB != null) {
                LB += landmarkLB.LB();
            }
            distanceCache[currQueryId] = LB;
            return LB;
        } else if (r instanceof Point){
            Point p = (Point) r;
            double[] x = p.mins();
            double dist = 0.0;

            for (int d : selectedVariatesIdx) {
                int offset = d * fourierLengthPerDimension();
                for (int i = offset; i < offset + fourierLengthPerDimension(); ++i) {
                    double maxDiff = x[i] - this.maxes()[i];
                    double minDiff = this.mins()[i] - x[i];
                    double diff = FastMath.max(0,FastMath.max(maxDiff, minDiff));
                    dist += diff * diff;
                }
            }
            distanceCache[currQueryId] = dist;
            return dist;
        } else {
            throw new IllegalArgumentException("Unsupported rectangle type");
        }
    }

    public MinimumBoundingRectangle mbr() {
        return this;
    }

    public String toString() {
        return "Rectangle [mins=" + Arrays.toString(this.mins) + ", maxes=" + Arrays.toString(this.maxes) + "]";
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + Arrays.hashCode(this.mins);
        result = 31 * result + Arrays.hashCode(this.maxes);
        return result;
    }

    public boolean equals(Object obj) {
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        final MinimumBoundingRectangle other = ObjectsHelper.asClass(obj, MinimumBoundingRectangle.class);
        if (other == null) {
            return false;
        } else {
            return Arrays.equals(this.mins, other.mins) && Arrays.equals(this.maxes, other.maxes);
        }
    }

    public double intersectionVolume(Rectangle r) {
        return !GeometryUtil.intersects(this.mins, this.maxes, r.mins(), r.maxes()) ? 0.0 :
                new MinimumBoundingRectangle(GeometryUtil.max(this.mins, r.mins()), GeometryUtil.min(this.maxes, r.maxes())).volume();
    }

    public Geometry geometry() {
        return this;
    }

    public double surfaceArea() {
        double sum = 0.0;

        for (int i = 0; i < this.mins.length; ++i) {
            double product = 1.0;

            for (int j = 0; j < this.mins.length; ++j) {
                if (i != j) {
                    product *= this.maxes[j] - this.mins[j];
                }
            }

            sum += product;
        }

        return 2.0 * sum;
    }

    public double volume() {
        double v = 1.0;

        for (int i = 0; i < this.mins.length; ++i) {
            v *= this.maxes[i] - this.mins[i];
        }

        return v;
    }

    public double logVolume(){
        double v = 0.0;

        for (int i = 0; i < this.mins.length; ++i) {
            double diff = this.maxes[i] - this.mins[i];
            if (diff != 0.0) {
                v += FastMath.log(diff);
            }
        }

        return v;
    }

    public double margin() {
        double sum = 0.0;

        for (int i = 0; i < this.mins.length; ++i) {
            sum += this.maxes[i] - this.mins[i];
        }

        return sum;
    }

    public double[] mins() {
        return this.mins;
    }

    public double[] maxes() {
        return this.maxes;
    }

    public double[] mids() {
        if (mids == null) {
            mids = new double[this.mins.length];
            for (int i = 0; i < this.mins.length; ++i) {
                mids[i] = (this.mins[i] + this.maxes[i]) / 2.0;
            }
        }
        return mids;
    }

    public int dimensions() {
        return this.mins.length;
    }
}
