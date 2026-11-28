package edu.sjsu.cs262.gossip.sim;

import edu.sjsu.cs262.gossip.churn.ChurnController;
import edu.sjsu.cs262.gossip.config.ExperimentConfig;
import edu.sjsu.cs262.gossip.data.TemperatureField;
import edu.sjsu.cs262.gossip.metrics.CsvWriter;
import edu.sjsu.cs262.gossip.metrics.Monitor;
import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.node.GossipNode;
import edu.sjsu.cs262.gossip.node.MomentumNode;
import edu.sjsu.cs262.gossip.node.NodeFactory;
import edu.sjsu.cs262.gossip.node.PairwiseNode;
import edu.sjsu.cs262.gossip.node.PushSumNode;
import edu.sjsu.cs262.gossip.topology.GraphBuilder;
import edu.sjsu.cs262.gossip.topology.GraphChecks;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One run: build the network, start everything, wait, stop, check conservation, and report.
 */
public final class Experiment {

    private static final int MAX_GRAPH_ATTEMPTS = 50;

    /**
     * End-of-run mass accounting, over every node that ever existed (including joiners).
     * expected = sum of all initial readings (S) and the number of nodes (W).
     * accounted = mass held by nodes + mass sitting in inboxes + mass dropped by the network.
     * stranded  = the part of that held by crashed nodes and their inboxes: mass lost to crashes.
     * dropped   = mass carried by messages the network discarded (random loss or dead target).
     * ok = accounted equals expected, within floating-point tolerance. If false, there is a bug.
     */
    public record Conservation(double expectedS, double expectedW, double accountedS, double accountedW,
                               double strandedS, double strandedW, double droppedS, double droppedW, boolean ok) {
    }

    /**
     * What a finished run reports: the final sample, the total traffic, and the mass accounting.
     */
    public record Result(Monitor.Sample last, Network.Stats traffic, Conservation conservation) {
    }

    private Experiment() {
    }

    public static Result run(ExperimentConfig cfg, Path csv) throws Exception {
        var graph = connectedGraph(cfg);
        var deg = GraphChecks.degreeStats(graph);
        System.out.printf(Locale.ROOT, "graph: n=%d R=%.4f degree min/mean/max = %d/%.1f/%d%n", cfg.n(), cfg.R(), deg.min(), deg.mean(), deg.max());

        var readings = TemperatureField.generate(graph, cfg);
        var net = new Network(cfg.lossProbability());
        var clock = new SimClock(cfg.meanTick().toNanos());

        NodeFactory factory = (id, x, y, v) -> switch (cfg.algorithm()) {
            case PAIRWISE -> new PairwiseNode(id, x, y, v, net, clock, cfg.seed());
            case PUSH_SUM -> new PushSumNode(id, x, y, v, net, clock, cfg.seed());
            case MOMENTUM -> new MomentumNode(id, x, y, v, net, clock, cfg.seed());
        };

        var nodes = new CopyOnWriteArrayList<GossipNode>();    // Monitor and ChurnController share this
        for (int i = 0; i < cfg.n(); i++) {
            var node = factory.create(i, graph.x()[i], graph.y()[i], readings.values()[i]);
            node.setNeighbors(graph.neighbors()[i]);
            net.register(node);
            nodes.add(node);
        }

        var out = new CsvWriter<>(csv, Monitor.Sample.class);
        var monitor = new Monitor(nodes, net, readings.mean(), cfg.meanTick(), cfg.sampleInterval(), out);

        try (var churn = cfg.churnRate() > 0 ? new ChurnController(cfg, nodes, net, factory) : null; out; monitor) {                    // monitor closes first, taking a final sample while nodes are running
            nodes.forEach(GossipNode::start);
            monitor.start();
            if (churn != null) churn.start();
            Thread.sleep(cfg.duration());
            // if (churn != null) churn.close();   // waits for any in-progress join, so the node list is final
        } finally {
            // safe to call twice; covers the exception path
            nodes.forEach(GossipNode::stop);    // stop, not crash: stopped nodes still count as alive
            for (var node : nodes) node.awaitStopped();   // all threads done, so the reads below are safe
        }

        var traffic = net.stats();
        return new Result(monitor.last(), traffic, checkConservation(nodes, traffic));
    }

    /**
     * Builds a graph, trying new positions (up to MAX_GRAPH_ATTEMPTS) until one is connected.
     */
    private static GraphBuilder.Graph connectedGraph(ExperimentConfig cfg) {
        for (int attempt = 0; attempt < MAX_GRAPH_ATTEMPTS; attempt++) {
            var g = GraphBuilder.build(cfg, attempt);
            if (GraphChecks.isConnected(g)) {
                if (attempt > 0)
                    System.out.println("graph: resampled " + attempt + " time(s) to get a connected graph");
                return g;
            }
        }
        throw new IllegalStateException("No connected graph in " + MAX_GRAPH_ATTEMPTS + " attempts. Increase R or n.");
    }

    /**
     * Call only after every node thread has stopped.
     */
    private static Conservation checkConservation(List<GossipNode> nodes, Network.Stats t) {
        double expS = 0, heldS = 0, heldW = 0, strS = 0, strW = 0, scale = nodes.size();
        for (var node : nodes) {
            expS += node.initial;
            scale += Math.abs(node.initial);
            var m = node.mass();
            var p = node.pendingMass();                 // delivered but never processed
            double s = m.s() + p.s(), w = m.w() + p.w();
            heldS += s;
            heldW += w;
            if (!node.isAlive()) {
                strS += s;
                strW += w;
            }
        }
        double expW = nodes.size();
        double accS = heldS + t.lostS(), accW = heldW + t.lostW();
        double tol = 1e-9 * scale;
        boolean ok = Math.abs(accS - expS) <= tol && Math.abs(accW - expW) <= tol;
        return new Conservation(expS, expW, accS, accW, strS, strW, t.lostS(), t.lostW(), ok);
    }
}