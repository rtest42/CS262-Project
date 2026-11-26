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
 * Known leak: if B updates and its Reply is then lost, A keeps its old value and mass is not conserved.
 * Message loss is where this baseline degrades, which is worth showing in the results.
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

    @Override
    protected void onTick() {
        if (pending && System.nanoTime() > deadline) pending = false;   // give up on a silent target
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
        switch (m) {
            case Message.Request(var from, var seq, var theirValue, var theirMomentum) -> {
                if (pending && System.nanoTime() <= deadline) {
                    net.send(from, new Message.Busy(id, seq));
                } else {
                    double old = value();
                    var r = exchange(theirValue, theirMomentum, old);
                    setMass(r.responder(), 1.0);
                    onExchanged(old, r.responder());
                    net.send(from, new Message.Reply(id, seq, r.initiator()));
                }
            }
            case Message.Reply(var from, var seq, var newValue) -> {
                if (pending && from == pendingTarget && seq == pendingSeq) {
                    double old = value();
                    setMass(newValue, 1.0);
                    onExchanged(old, newValue);
                    pending = false;
                }                                        // otherwise a stale reply after a timeout: ignore
            }
            case Message.Busy(var from, var seq) -> {
                if (pending && from == pendingTarget && seq == pendingSeq) pending = false;
            }
            case Message.Share _ -> { }                  // push-sum message, not used here
        }
    }
}