package edu.sjsu.cs262.gossip.topology;

import edu.sjsu.cs262.gossip.topology.GraphBuilder.Graph;

import java.util.Arrays;

/**
 * Sanity checks to run on a graph before any experiment.
 */
public final class GraphChecks {

    public record DegreeStats(int min, double mean, int max) {
    }

    private GraphChecks() {
    }

    /**
     * Sizes of the connected components, found with BFS. A connected graph gives exactly one entry.
     */
    public static int[] componentSizes(Graph g) {
        int n = g.size();
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int[] sizes = new int[n];
        int count = 0;
        for (int start = 0; start < n; start++) {
            if (seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                for (int v : g.neighbors()[queue[head++]]) {
                    if (!seen[v]) {
                        seen[v] = true;
                        queue[tail++] = v;
                    }
                }
            }
            sizes[count++] = tail;
        }
        return Arrays.copyOf(sizes, count);
    }

    public static boolean isConnected(Graph g) {
        return componentSizes(g).length == 1;
    }

    public static DegreeStats degreeStats(Graph g) {
        var s = Arrays.stream(g.neighbors()).mapToInt(a -> a.length).summaryStatistics();
        return new DegreeStats(s.getMin(), s.getAverage(), s.getMax());
    }

    /**
     * Throws with a readable message if the graph is disconnected, so a bad R fails fast.
     */
    public static void requireConnected(Graph g) {
        int[] sizes = componentSizes(g);
        if (sizes.length > 1) {
            throw new IllegalStateException("Graph is disconnected: " + sizes.length + " components, largest has " +
                    Arrays.stream(sizes).max().getAsInt() + " of " + g.size() + " nodes. Increase R or change the seed.");
        }
    }
}