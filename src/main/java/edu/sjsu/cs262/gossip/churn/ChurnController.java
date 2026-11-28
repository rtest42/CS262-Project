package edu.sjsu.cs262.gossip.churn;

import edu.sjsu.cs262.gossip.config.ExperimentConfig;
import edu.sjsu.cs262.gossip.data.TemperatureField;
import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.node.GossipNode;
import edu.sjsu.cs262.gossip.node.NodeFactory;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies churn as a Poisson process with cfg.churnRate() EVENTS per second for the whole network.
 * Each event is a fair coin flip between a crash and a join, so the crash rate alone is about
 * churnRate / 2 (and the population stays roughly constant). When only MIN_ALIVE nodes are left,
 * every event is a join.
 * crash: a random alive node dies silently. Its mass is lost. Its neighbors keep sending to it
 * (and losing mass in push-sum) until they notice, DETECTION_TICKS mean ticks later.
 * join:  a new node appears at a random position, linked to every alive node within R,
 * with a fresh reading from the temperature field.
 * Everything runs on one scheduler thread, so neighbor lists have a single writer.
 */
public final class ChurnController implements AutoCloseable {

    private static final int DETECTION_TICKS = 50;
    private static final int MIN_ALIVE = 2;

    private final ExperimentConfig cfg;
    private final List<GossipNode> nodes;       // shared with Monitor; must be a CopyOnWriteArrayList
    private final Network net;
    private final NodeFactory factory;
    private final SplittableRandom rng;
    private final long detectionNanos;
    private int nextId;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofPlatform().daemon().name("churn").unstarted(r));

    public ChurnController(ExperimentConfig cfg, List<GossipNode> nodes, Network net, NodeFactory factory) {
        if (cfg.churnRate() <= 0) throw new IllegalArgumentException("churnRate must be > 0");
        this.cfg = cfg;
        this.nodes = nodes;
        this.net = net;
        this.factory = factory;
        this.rng = new SplittableRandom(cfg.seed() ^ 0x2545F4914F6CDD1DL);   // separate from the other streams
        this.detectionNanos = DETECTION_TICKS * cfg.meanTick().toNanos();
        this.nextId = nodes.size();                                        // initial ids are 0..n-1
    }

    public void start() {
        scheduleNext();
    }

    @Override
    public void close() {
        exec.shutdownNow();
        try {
            // wait for a join() that is mid-flight, so the node list is final before Experiment stops the nodes
            boolean terminated = exec.awaitTermination(1, TimeUnit.SECONDS);
            if (!terminated) {
                System.err.println("Executor did not terminate within 1 second");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleNext() {
        long delayNanos = (long) (-Math.log(1.0 - rng.nextDouble()) / cfg.churnRate() * 1e9);
        try {
            exec.schedule(this::fire, delayNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException closed) {
            // close() was called; stop scheduling
        }
    }

    private void fire() {
        try {
            var alive = nodes.stream().filter(GossipNode::isAlive).toList();
            if (alive.size() > MIN_ALIVE && rng.nextBoolean()) {
                crash(alive.get(rng.nextInt(alive.size())));
            } else {
                join(alive);
            }
        } catch (RuntimeException e) {
            System.err.println("ChurnController: event failed: " + e);
        } finally {
            scheduleNext();
        }
    }

    private void crash(GossipNode victim) {
        victim.crash();
        exec.schedule(() -> prune(victim), detectionNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Neighbors finally notice the crash and drop the dead node from their lists.
     */
    private void prune(GossipNode victim) {
        for (int id : victim.neighbors()) {
            var nb = net.node(id);
            if (nb != null && nb.isAlive()) {
                nb.setNeighbors(Arrays.stream(nb.neighbors()).filter(i -> i != victim.id).toArray());
            }
        }
    }

    private void join(List<GossipNode> alive) {
        int id = nextId++;
        double x = rng.nextDouble(cfg.L()), y = rng.nextDouble(cfg.L());
        var node = factory.create(id, x, y, TemperatureField.reading(cfg, x, rng));

        double r2 = cfg.R() * cfg.R();
        int[] near = alive.stream()
                .filter(o -> (o.x - x) * (o.x - x) + (o.y - y) * (o.y - y) <= r2)
                .mapToInt(o -> o.id)
                .toArray();

        net.register(node);
        nodes.add(node);
        node.setNeighbors(near);
        node.start();
        for (int nid : near) {                  // make the links symmetric
            var nb = net.node(nid);
            int[] old = nb.neighbors();
            int[] grown = Arrays.copyOf(old, old.length + 1);
            grown[old.length] = id;
            nb.setNeighbors(grown);
        }
    }
}