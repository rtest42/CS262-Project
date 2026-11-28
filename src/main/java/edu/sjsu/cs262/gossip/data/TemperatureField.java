package edu.sjsu.cs262.gossip.data;

import edu.sjsu.cs262.gossip.config.ExperimentConfig;
import edu.sjsu.cs262.gossip.topology.GraphBuilder.Graph;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Initial sensor readings, and the true average that every error is measured against.
 */
public final class TemperatureField {

    /**
     * values[i] is node i's reading. mean is the true average of all of them at time zero.
     */
    public record Readings(double[] values, double mean) {
    }

    private static final double NOISE_SD = 0.5;

    private TemperatureField() {
    }

    public static Readings generate(Graph g, ExperimentConfig cfg) {
        // distinct from the graph stream (~seed) and the node streams (seed ^ id)
        var rng = new SplittableRandom(cfg.seed() ^ 0x9E3779B97F4A7C15L);
        double[] v = new double[g.size()];
        for (int i = 0; i < v.length; i++) v[i] = reading(cfg, g.x()[i], rng);
        return new Readings(v, Arrays.stream(v).average().orElseThrow());
    }

    /**
     * One reading for a node at horizontal position x. Also used for nodes that join during churn.
     */
    public static double reading(ExperimentConfig cfg, double x, java.util.random.RandomGenerator rng) {
        double L = cfg.L();
        return switch (cfg.field()) {
            case IID -> 20 + 5 * rng.nextGaussian();                       // independent per node
            case SMOOTH -> 10 + 30 * x / L + NOISE_SD * rng.nextGaussian();   // gradient: slowest mode to average out
            case HALF -> (x < L / 2 ? 30 : 10) + NOISE_SD * rng.nextGaussian();  // hot left, cold right
        };
    }
}