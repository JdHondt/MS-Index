package net.jelter.utils;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

import static net.jelter.utils.Parameters.qLen;

@RequiredArgsConstructor
public class Range implements Serializable {
    @NonNull @Getter @Setter
    private int start;

    @NonNull @Getter @Setter
    private int end;

    public Tuple2<List<Range>, Double> optimalPlan;

    public int getPower2Length() {
        return lib.nextPowerOfTwo(end - start + qLen);
    }
    public int getFullLength() {
        return end - start + qLen;
    }

    public int getLength() {
        return end - start + 1;
    }

    public void reset(){optimalPlan = null;}

    public int compareTo(Range range) {
        return Integer.compare(this.start, range.start);
    }

    public String toString() {
        return String.format("[%d, %d]", start, end);
    }

    public boolean overlaps(Range other) {
        return this.start <= other.end && other.start <= this.end;
    }

    public boolean neighbours(Range other) {return this.end == other.start - 1 || this.start == other.end + 1;}

    public Range union(Range other) {
        return new Range(Math.min(this.start, other.start), Math.max(this.end, other.end));
    }

    public Range intersection(Range other) {
        return new Range(Math.max(this.start, other.start), Math.min(this.end, other.end));
    }
}
