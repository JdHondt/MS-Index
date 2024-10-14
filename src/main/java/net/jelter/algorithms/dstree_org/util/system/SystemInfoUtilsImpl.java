package net.jelter.algorithms.dstree_org.util.system;

public class SystemInfoUtilsImpl implements SystemInfoUtils {
    private final Runtime rt = Runtime.getRuntime();

    public SystemInfoUtilsImpl() {
    }

    public long getTotalMemory() {
        long totalMemory = rt.maxMemory();
        return totalMemory / 0x100000L;
    }

}
