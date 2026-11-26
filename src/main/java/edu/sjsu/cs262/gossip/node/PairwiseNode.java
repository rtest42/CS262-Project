package edu.sjsu.cs262.gossip.node;

import edu.sjsu.cs262.gossip.net.Message;
import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.sim.SimClock;

/**
 * Boyd et al. pairwise averaging as a request/reply exchange.
 *
 * Mass is conserved because a node that has an outstanding request does not change its value:
 * it declines incoming requests with Busy until its own exchange finishes or times out.
 * A request that gets no answer (dead target, lost message) times out and the node keeps its old value.
 * The responder replies with a delta, and the initiator applies every delta it receives, even a late one,
 * so mass is conserved no matter how exchanges interleave. The one remaining leak is a Reply that the
 * network drops (random loss, or an initiator that crashed): the responder has moved and the initiator never does.
 * That leak is counted in Network.Stats.lostS, and message loss is where this baseline degrades.
 */
public class PairwiseNode extends GossipNode {

    private static final int TIMEOUT_TICKS = 10;

    private boolean pending;
    private int pendingTarget, pendingSeq, seqCounter;
    private long deadline;

    public PairwiseNode(int id, double x, double y, double initialValue,
                        Network net, SimClock clock, long seed) {
        super(id, x, y, initialValue, net, clock, seed);
    }

    /** What an exchange produces for each side. */
    protected record Exchange(double initiator, double responder) {}

    /** Momentum the initiator attaches to its request. Plain pairwise has none. */
    protected double momentum() { return 0; }

    /** Responder decides both new values. They must sum to initiatorValue + responderValue to conserve mass. */
    protected Exchange exchange(double initiatorValue, double initiatorMomentum, double responderValue) {
        double avg = (initiatorValue + responderValue) / 2;
        return new Exchange(avg, avg);
    }

    /** Called on each side after an exchange changed its value. */
    protected void onExchanged(double oldValue, double newValue) {}

    private double value() { return mass().s(); }       // w stays 1 for pairwise

    /** Gives up on a silent target once the deadline has passed. */
    private void expirePending() {
        if (pending && System.nanoTime() > deadline) pending = false;
    }

    @Override
    protected void onTick() {
        expirePending();
        if (pending) return;
        int target = pickNeighbor();
        if (target < 0) return;
        pending = true;
        pendingTarget = target;
        pendingSeq = ++seqCounter;
        deadline = System.nanoTime() + TIMEOUT_TICKS * clock.meanTickNanos();
        net.send(target, new Message.Request(id, pendingSeq, value(), momentum()));
    }

    @Override
    protected void onMessage(Message m) {
        expirePending();
        switch (m) {
            case Message.Request(var from, var seq, var theirValue, var theirMomentum) -> {
                if (pending) {
                    net.send(from, new Message.Busy(id, seq));
                } else {
                    double old = value();
                    var r = exchange(theirValue, theirMomentum, old);
                    setMass(r.responder(), 1.0);
                    onExchanged(old, r.responder());
                    // r.initiator() + r.responder() == theirValue + old, so this delta is exactly what we gave up
                    net.send(from, new Message.Reply(id, seq, r.initiator() - theirValue));
                }
            }
            case Message.Reply(var from, var seq, var delta) -> {
                double old = value();
                setMass(old + delta, 1.0);                     // always apply: the responder has already moved
                onExchanged(old, old + delta);
                if (pending && from == pendingTarget && seq == pendingSeq) pending = false;
            }
            case Message.Busy(var from, var seq) -> {
                if (pending && from == pendingTarget && seq == pendingSeq) pending = false;
            }
            case Message.Share _ -> { }                        // push-sum message, not used here
        }
    }
}