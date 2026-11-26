package edu.sjsu.cs262.gossip.metrics;

import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.node.GossipNode;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Samples the whole network at a fixed interval and writes one CSV row per sample.
 * Only nodes that are alive are measured. The nodes collection must be safe to iterate while
 * other threads add to it (CopyOnWriteArrayList or ConcurrentLinkedQueue), because churn adds nodes.
 */
public final class Monitor implements AutoCloseable {

    /**
     * One row of the output.
     * rmsError / maxError: spread of the alive nodes' estimates around the original true mean.
     * rmsErrorSurvivors: same, but around the mean of the alive nodes' initial readings.
     * bias: average alive estimate minus the original true mean (drift caused by lost mass).
     * totalS / totalW: sums of s and w over alive nodes (excludes messages still in flight).
     */
    public record Sample(double tSec, long messages, int alive,
                         double rmsError, double maxError, double rmsErrorSurvivors,
                         double bias, double totalS, double totalW) {}

    private final Collection<GossipNode> nodes;
    private final Network net;
    private final double trueMean;
    private final Duration interval;
    private final CsvWriter<Sample> out;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofPlatform().daemon().name("monitor").unstarted(r));

    private volatile Sample last;
    private long t0;

    public Monitor(Collection<GossipNode> nodes, Network net, double trueMean,
                   Duration interval, CsvWriter<Sample> out) {
        this.nodes = nodes;
        this.net = net;
        this.trueMean = trueMean;
        this.interval = interval;
        this.out = out;
    }

    /** Starts sampling; the first sample is taken immediately, so the curve begins at t = 0. */
    public void start() {
        t0 = System.nanoTime();
        exec.scheduleAtFixedRate(this::record, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** The most recent sample, or null before the first one. */
    public Sample last() { return last; }

    /** Stops the sampler and takes one final sample, so the run's end state is always recorded. */
    @Override
    public void close() throws InterruptedException {
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
        record();
    }

    private void record() {
        try {
            var s = sample();
            last = s;
            out.write(s);
        } catch (IOException | RuntimeException e) {    // an uncaught exception would silently stop the schedule
            System.err.println("Monitor: sample failed: " + e);
        }
    }

    private Sample sample() {
        double t = (System.nanoTime() - t0) / 1e9;
        long msgs = net.stats().sent();
        var alive = nodes.stream().filter(GossipNode::isAlive).toList();
        int k = alive.size();
        if (k == 0) {
            return new Sample(t, msgs, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }

        double[] est = new double[k];
        double sumEst = 0, sumInit = 0, totS = 0, totW = 0;
        for (int i = 0; i < k; i++) {
            var node = alive.get(i);
            var m = node.mass();                        // one atomic (s, w) snapshot per node
            est[i] = m.s() / m.w();
            sumEst += est[i];
            sumInit += node.initial;
            totS += m.s();
            totW += m.w();
        }

        double survivorsMean = sumInit / k;
        double sq = 0, sqSurv = 0, max = 0;
        for (double e : est) {
            double d = e - trueMean, ds = e - survivorsMean;
            sq += d * d;
            sqSurv += ds * ds;
            max = Math.max(max, Math.abs(d));
        }
        return new Sample(t, msgs, k, Math.sqrt(sq / k), max, Math.sqrt(sqSurv / k),
                sumEst / k - trueMean, totS, totW);
    }
}