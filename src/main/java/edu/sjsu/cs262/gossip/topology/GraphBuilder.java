package edu.sjsu.cs262.gossip.topology;

import edu.sjsu.cs262.gossip.config.ExperimentConfig;

import java.util.SplittableRandom;
import java.util.stream.IntStream;

/** Builds a random geometric graph: n points uniform in an L x L square, linked if within distance R. */
public final class GraphBuilder {

    /** Node i sits at (x[i], y[i]) and its neighbors are neighbors[i]. Links are symmetric. */
    public record Graph(double[] x, double[] y, int[][] neighbors) {
        public int size() { return x.length; }
    }

    private GraphBuilder() {}

    public static Graph build(ExperimentConfig cfg, int attempt) {
        int n = cfg.n();
        // ~seed keeps this stream apart from the per-node streams (seed ^ id)
        var rng = new SplittableRandom(~cfg.seed() + attempt);
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rng.nextDouble(cfg.L());
            y[i] = rng.nextDouble(cfg.L());
        }
        double r2 = cfg.R() * cfg.R();
        int[][] nb = new int[n][];
        for (int i = 0; i < n; i++) {          // O(n^2), fine at the sizes this project uses
            int self = i;
            nb[i] = IntStream.range(0, n)
                    .filter(j -> j != self && dist2(x, y, self, j) <= r2)
                    .toArray();
        }
        return new Graph(x, y, nb);
    }

    public static Graph build(ExperimentConfig cfg) {
        return build(cfg, 0);
    }

    static double dist2(double[] x, double[] y, int i, int j) {
        double dx = x[i] - x[j], dy = y[i] - y[j];
        return dx * dx + dy * dy;
    }
}