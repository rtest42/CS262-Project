package edu.sjsu.cs262.gossip.net;

import edu.sjsu.cs262.gossip.node.GossipNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Nodes only ever talk through this. It is the one place that drops messages to dead nodes,
 * applies random loss, and counts traffic, so every algorithm is measured the same way.
 * It also totals the mass carried by dropped messages, so the end-of-run conservation check can account for it.
 * Random loss draws from ThreadLocalRandom, so loss patterns are not reproducible from the seed.
 */
public final class Network {

    /**
     * Snapshot of the counters. sent = every transmission attempt, whether or not it arrived.
     * lostS / lostW = total (s, w) mass carried by messages that were dropped (random loss or dead target).
     */
    public record Stats(long sent, long lost, long toDead, double lostS, double lostW) {
        public long delivered() { return sent - lost - toDead; }
    }

    private final Map<Integer, GossipNode> nodes = new ConcurrentHashMap<>();
    private final double lossProbability;
    private final LongAdder sent = new LongAdder(), lost = new LongAdder(), toDead = new LongAdder();
    private final DoubleAdder lostS = new DoubleAdder(), lostW = new DoubleAdder();

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
            recordLostMass(m);
            return;
        }
        var target = nodes.get(to);
        if (target == null || !target.isAlive()) {
            toDead.increment();
            recordLostMass(m);
            return;
        }
        target.deliver(m);
    }

    /** Adds the mass a dropped message was carrying to the lost-mass totals. Messages with no mass add nothing. */
    private void recordLostMass(Message m) {
        switch (m) {
            case Message.Share(var from, var s, var w) -> { lostS.add(s); lostW.add(w); }
            case Message.Reply(var from, var seq, var delta) -> lostS.add(delta);   // only valid after the Reply change
            default -> { }                                                           // Request and Busy carry no mass
        }
    }

    public Stats stats() {
        return new Stats(sent.sum(), lost.sum(), toDead.sum(), lostS.sum(), lostW.sum());
    }
}