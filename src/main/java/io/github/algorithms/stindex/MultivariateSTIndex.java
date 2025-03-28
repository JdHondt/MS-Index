package io.github.algorithms.stindex;

import io.github.algorithms.BaselineWrapper;
import com.github.davidmoten.rtreemulti.geometry.Point;
import io.github.utils.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.utils.Parameters.*;

public class MultivariateSTIndex extends BaselineWrapper {
    STIndex[] faloutsos;
    private FourierTrail[] fourierTrails;

    public List<CandidateSegment> approxKNN(int k, double[][] query){
        return lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .flatMap(i -> faloutsos[i].closest(Point.create(query[i]), k).stream())
                .map(Tuple2::_1)
                .collect(Collectors.toList());
    }

    public List<CandidateSegment> thresholdQuery(double[] thresholds, double[][] query) {
        return lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .flatMap(i -> faloutsos[i].getBelowThreshold(query[i], thresholds[i]).stream())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    public void buildIndex() {
//        First get all the fourierTrails, then build the indexes
        fourierTrails = new FourierTrail[N];
        for (int i = 0; i < N; i++) {
            fourierTrails[i] = DFTUtils.getFourierTrail(i, null);
        }

        faloutsos = new STIndex[channels];
        for (int d = 0; d < channels; d++) {
            faloutsos[d] = new STIndex(d, fourierTrails);
        }
        System.out.println("Index built");
        System.out.println("Number of nodes: " + countNodes());
    }

    @Override
    public double memoryUsage() {
        long total = 0;
        for (int d = 0; d < channels; d++) {
            total += faloutsos[d].memoryUsage();
        }
        return total / 1000000d;
    }

    private long countNodes() {
        long total = 0;
        for (int d = 0; d < channels; d++) {
            total += faloutsos[d].countNodes();
        }
        return total;
    }
}
