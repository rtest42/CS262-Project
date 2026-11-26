package edu.sjsu.cs262.gossip.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Set;

/**
 * Everything one run needs. Every other class reads from this, and seed makes runs reproducible.
 *
 * @param n               number of nodes
 * @param L               side of the square the nodes are placed in
 * @param R               two nodes are neighbors if they are within this distance
 * @param seed            master seed for positions, initial values, and per-node randomness
 * @param algorithm       which node class to run
 * @param field           shape of the initial temperature readings
 * @param churnRate       network-wide crashes per second (0 = no churn)
 * @param lossProbability chance that any single message is dropped
 * @param meanTick        mean time between a node's clock ticks
 * @param duration        how long the run lasts
 * @param sampleInterval  how often the Monitor records a sample
 */
public record ExperimentConfig(
        int n, double L, double R, long seed, Algorithm algorithm, Field field,
        double churnRate, double lossProbability,
        Duration meanTick, Duration duration, Duration sampleInterval) {

    public enum Algorithm { PAIRWISE, PUSH_SUM, MOMENTUM }

    /** Shape of the initial readings: independent noise, a smooth left-to-right gradient, or a hot half and a cold half. */
    public enum Field { IID, SMOOTH, HALF }

    private static final Set<String> KEYS = Set.of(
            "n", "L", "R", "seed", "algorithm", "field", "churn", "loss", "tickMs", "durationSec", "sampleMs");

    public ExperimentConfig {
        if (n < 2)                                throw new IllegalArgumentException("n must be >= 2");
        if (L <= 0 || R <= 0)                     throw new IllegalArgumentException("L and R must be > 0");
        if (churnRate < 0)                        throw new IllegalArgumentException("churn must be >= 0");
        if (lossProbability < 0 || lossProbability >= 1)
            throw new IllegalArgumentException("loss must be in [0, 1)");
        if (meanTick.isZero() || meanTick.isNegative() || duration.isNegative() || sampleInterval.isZero())
            throw new IllegalArgumentException("durations must be positive");
    }

    /** Radius at which a random geometric graph becomes connected with high probability (asymptotically). */
    public static double connectivityRadius(int n, double L) {
        return L * Math.sqrt(Math.log(n) / (Math.PI * n));
    }

    /** Parses key=value arguments, e.g. {@code n=200 algorithm=PUSH_SUM churn=0.5 durationSec=30}. */
    public static ExperimentConfig fromArgs(String... args) {
        var kv = new HashMap<String, String>();
        for (var a : args) {
            var p = a.split("=", 2);
            if (p.length != 2 || !KEYS.contains(p[0]))
                throw new IllegalArgumentException("Unknown argument '" + a + "'. Keys: " + KEYS);
            kv.put(p[0], p[1]);
        }
        int n = Integer.parseInt(kv.getOrDefault("n", "200"));
        double L = Double.parseDouble(kv.getOrDefault("L", "1.0"));
        // default radius: 1.5x the connectivity threshold, so the graph is connected but still sparse
        double R = kv.containsKey("R") ? Double.parseDouble(kv.get("R")) : 1.5 * connectivityRadius(n, L);
        return new ExperimentConfig(
                n, L, R,
                Long.parseLong(kv.getOrDefault("seed", "42")),
                Algorithm.valueOf(kv.getOrDefault("algorithm", "PUSH_SUM").toUpperCase()),
                Field.valueOf(kv.getOrDefault("field", "IID").toUpperCase()),
                Double.parseDouble(kv.getOrDefault("churn", "0")),
                Double.parseDouble(kv.getOrDefault("loss", "0")),
                Duration.ofMillis(Long.parseLong(kv.getOrDefault("tickMs", "10"))),
                Duration.ofSeconds(Long.parseLong(kv.getOrDefault("durationSec", "30"))),
                Duration.ofMillis(Long.parseLong(kv.getOrDefault("sampleMs", "100"))));
    }
}