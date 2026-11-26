package edu.sjsu.cs262.gossip.net;

import edu.sjsu.cs262.gossip.node.GossipNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Nodes only ever talk through this. It is the one place that drops messages to dead nodes,
 * applies random loss, and counts traffic, so every algorithm is measured the same way.
 */
public final class Network {

    /** Snapshot of the counters. sent = every transmission attempt, whether or not it arrived. */
    public record Stats(long sent, long lost, long toDead) {
        public long delivered() { return sent - lost - toDead; }
    }

    private final Map<Integer, GossipNode> nodes = new ConcurrentHashMap<>();
    private final double lossProbability;
    private final LongAdder sent = new LongAdder(), lost = new LongAdder(), toDead = new LongAdder();

    public Network(double lossProbability) {
        this.lossProbability = lossProbability;
    }

    public void register(GossipNode node) { nodes.put(node.id, node); }

    /** Removes a node entirely, as when it leaves the network instead of crashing. */
    public void unregister(int id)        { nodes.remove(id); }

    public GossipNode node(int id)        { return nodes.get(id); }

    public void send(int to, Message m) {
        sent.increment();
        if (lossProbability > 0 && ThreadLocalRandom.current().nextDouble() < lossProbability) {
            lost.increment();
            return;
        }
        var target = nodes.get(to);
        if (target == null || !target.isAlive()) {
            toDead.increment();
            return;
        }
        target.deliver(m);
    }

    public Stats stats() {
        return new Stats(sent.sum(), lost.sum(), toDead.sum());
    }
}