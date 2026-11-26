package edu.sjsu.cs262.gossip.node;

import edu.sjsu.cs262.gossip.net.Message;
import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.sim.SimClock;

import java.util.SplittableRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for every gossip algorithm. Subclasses implement onTick() and onMessage() only.
 * All state changes happen on the node's own thread, so no locks are needed inside a node.
 */
public abstract class GossipNode implements Runnable {

    /** Immutable (sum, weight) pair. Swapped atomically so the Monitor never sees a torn read. */
    public record Mass(double s, double w) {}

    public final int id;
    public final double x, y;
    public final double initial;                 // the node's starting reading, never modified

    protected final Network net;
    protected final SplittableRandom rng;       // per-node, seeded from config seed + id
    protected final SimClock clock;

    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
    private volatile boolean alive = true;
    private volatile int[] neighbors = new int[0];   // immutable snapshot, replaced wholesale
    private volatile Mass mass;
    private Thread thread;

    protected GossipNode(int id, double x, double y, double initialValue,
                         Network net, SimClock clock, long seed) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.net = net;
        this.clock = clock;
        this.rng = new SplittableRandom(seed ^ id);
        this.initial = initialValue;
        this.mass = new Mass(initialValue, 1.0);     // pairwise just keeps w = 1
    }

    // ---- what subclasses implement ----
    protected abstract void onTick();
    protected abstract void onMessage(Message m);

    // ---- state, readable from any thread (Monitor) ----
    public Mass mass()        { return mass; }
    public double estimate()  { var m = mass; return m.s() / m.w(); }
    protected void setMass(double s, double w) { mass = new Mass(s, w); }

    // ---- lifecycle and wiring (called by Experiment / ChurnController / Network) ----
    public void setNeighbors(int[] ids) { neighbors = ids.clone(); }
    public int[] neighbors()            { return neighbors; }   // shared snapshot: callers must not modify it
    public boolean isAlive()            { return alive; }
    public void deliver(Message m)      { inbox.offer(m); }

    public void start() { thread = Thread.ofVirtual().name("node-" + id).start(this); }

    /** Crash: stops the loop. The node's mass is lost, which is exactly what the bias metric measures. */
    public void crash() {
        alive = false;
        if (thread != null) thread.interrupt();
    }

    /** Uniform random neighbor, or -1 if none. Override for smarter selection in a variation. */
    protected int pickNeighbor() {
        var n = neighbors;
        return n.length == 0 ? -1 : n[rng.nextInt(n.length)];
    }

    @Override
    public final void run() {
        try {
            long next = System.nanoTime() + clock.nextDelayNanos(rng);
            while (alive) {
                long wait = next - System.nanoTime();
                Message m = wait > 0 ? inbox.poll(wait, TimeUnit.NANOSECONDS) : null;
                if (m != null) {
                    onMessage(m);
                } else {
                    onTick();
                    next = System.nanoTime() + clock.nextDelayNanos(rng);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();     // crash() or shutdown
        }
    }
}