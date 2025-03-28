package io.github.algorithms.dstree_org;

import io.github.utils.CandidateMVSubsequence;
import io.github.utils.CandidateSegment;
import io.github.utils.lib;
import lombok.Getter;
import io.github.algorithms.Algorithm;
import io.github.algorithms.BaselineWrapper;
import io.github.utils.CandidateSubsequence;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.io.DataManager.data;
import static io.github.utils.Parameters.*;


public class MVDSTreeOrg extends BaselineWrapper implements Serializable {
    @Getter
    public DSTreeOrg[] indices;

    public MVDSTreeOrg() {
        indices = new DSTreeOrg[channels];
        for (int i = 0; i < channels; i++) {
            indices[i] = new DSTreeOrg();
        }
    }

    @Override
    public void buildIndex() {
        final CandidateSubsequence[][][] subsequences = new CandidateSubsequence[N][channels][];
        for (int n = 0; n < N; n++) {
            for (int d = 0; d < channels; d++) {
                int nSubsequences = data[n][d].length - qLen + 1;
                subsequences[n][d] = new CandidateSubsequence[nSubsequences];
                for (int i = 0; i < nSubsequences; i++) {
                    subsequences[n][d][i] = new CandidateSubsequence(n, d, i);
                }
            }
        }

        lib.getStream(IntStream.range(0, channels).boxed()).forEach(i -> indices[i].buildIndex(subsequences, i));
    }

    @Override
    public void saveIndex(){
        String fileName = getIndexPath();

//        Serialize this object
        try {
            FileOutputStream fileOut = new FileOutputStream(fileName);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Algorithm loadIndex(){
        String fileName = getIndexPath();

        try {
            FileInputStream fileIn = new FileInputStream(fileName);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            return (MVDSTreeOrg) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<CandidateSegment> approxKNN(int k, double[][] query){
        return lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .flatMap(i -> indices[i].approxKNN(query[i], k).stream())
                .map(CandidateSubsequence::toMVSubsequence)
                .distinct()
                .map(CandidateMVSubsequence::toCandidateSegment)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<CandidateSegment> thresholdQuery(double[] thresholds, double[][] query) {
        return lib.getStream(IntStream.of(selectedVariatesIdx).boxed())
                .flatMap(i -> indices[i].thresholdQuery(query[i], thresholds[i]).stream())
                .map(CandidateSubsequence::toMVSubsequence)
                .distinct()
                .map(CandidateMVSubsequence::toCandidateSegment)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public double memoryUsage() {
//        Get the size of the saved index by extracting the size of the serialized file
        double totalSize = 0;
        for (DSTreeOrg index : indices) {
            totalSize += index.memoryUsage();
        }
        return totalSize;
    }
}
