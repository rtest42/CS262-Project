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
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** One run: build the network, start everything, wait, stop, and report. */
public final class Experiment {

    /** What a finished run reports: the final sample and the total traffic. */
    public record Result(Monitor.Sample last, Network.Stats traffic) {}

    private Experiment() {}

    public static Result run(ExperimentConfig cfg, Path csv) throws Exception {
        var graph = GraphBuilder.build(cfg);
        GraphChecks.requireConnected(graph);
        var deg = GraphChecks.degreeStats(graph);
        System.out.printf(Locale.ROOT, "graph: n=%d R=%.4f degree min/mean/max = %d/%.1f/%d%n",
                cfg.n(), cfg.R(), deg.min(), deg.mean(), deg.max());

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
        var monitor = new Monitor(nodes, net, readings.mean(), cfg.sampleInterval(), out);
        var churn = cfg.churnRate() > 0 ? new ChurnController(cfg, nodes, net, factory) : null;

        try (out; monitor) {                    // monitor closes first, taking a final sample while nodes are alive
            nodes.forEach(GossipNode::start);
            monitor.start();
            if (churn != null) churn.start();
            Thread.sleep(cfg.duration());
            if (churn != null) churn.close();   // stop churn before the final sample
        } finally {
            if (churn != null) churn.close();
            nodes.forEach(GossipNode::crash);   // stops every node thread, including any that joined
        }
        return new Result(monitor.last(), net.stats());
    }
}