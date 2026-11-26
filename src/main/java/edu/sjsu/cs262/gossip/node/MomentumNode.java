package edu.sjsu.cs262.gossip.node;

import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.sim.SimClock;

/**
 * Momentum-inspired pairwise gossip, adapted for asynchronous nodes. In the paper, call it that,
 * not "heavy-ball gossip" or "Loizou and Richtarik".
 *
 * This is NOT the update from Loizou and Richtarik. In their protocols every node updates its value
 * at every step and only a subset exchange messages, which needs a shared global step. Here nodes
 * tick independently, so each node remembers lastChange, the signed change from its most recent
 * exchange, and the pair uses it like this:
 *
 *   avg = (a + b) / 2                      a = initiator, b = responder
 *   e   = BETA * (lastChange_a - lastChange_b) / 2
 *   new a = avg + e,  new b = avg - e
 *
 * The two new values always sum to a + b, so mass is conserved exactly (apart from lost messages).
 * After the exchange the pair is left apart by BETA * (lastChange_a - lastChange_b), an overshoot
 * in the direction the initiator has been moving relative to the responder.
 * BETA = 0 gives plain pairwise averaging.
 *
 * Whether this accelerates anything is an empirical question for the experiments. As a rough
 * heuristic, keep BETA below 0.5 or the residual momentum can fail to die out; that bound is
 * untested here.
 *
 * Caution: the overshoot term can raise the pair's difference whenever BETA * |lastChange_a - lastChange_b|
 * exceeds |a - b|, so unlike plain averaging an exchange can increase variance. Compare against
 * PAIRWISE and report it as a result if this turns out slower or unstable.
 */
public class MomentumNode extends PairwiseNode {

    private static final double BETA = 0.3;

    private double lastChange;      // only touched on this node's own thread

    public MomentumNode(int id, double x, double y, double initialValue,
                        Network net, SimClock clock, long seed) {
        super(id, x, y, initialValue, net, clock, seed);
    }

    @Override
    protected double momentum() { return lastChange; }

    @Override
    protected Exchange exchange(double initiatorValue, double initiatorMomentum, double responderValue) {
        double avg = (initiatorValue + responderValue) / 2;
        double e = BETA * (initiatorMomentum - lastChange) / 2;     // lastChange here is the responder's
        return new Exchange(avg + e, avg - e);
    }

    @Override
    protected void onExchanged(double oldValue, double newValue) {
        lastChange = newValue - oldValue;
    }
}