package edu.sjsu.cs262.gossip.node;

import edu.sjsu.cs262.gossip.net.Message;
import edu.sjsu.cs262.gossip.net.Network;
import edu.sjsu.cs262.gossip.sim.SimClock;

/**
 * Kempe et al. push-sum. On each tick, keep half of (s, w) and push the other half to a neighbor.
 * Incoming shares are simply added. The estimate s/w converges to the true mean as long as no mass is lost.
 * Mass sent to a dead node or dropped by the network is lost, which is the bias the Monitor measures.
 */
public class PushSumNode extends GossipNode {

    public PushSumNode(int id, double x, double y, double initialValue, Network net, SimClock clock, long seed) {
        super(id, x, y, initialValue, net, clock, seed);
    }

    @Override
    protected void onTick() {
        int target = pickNeighbor();
        if (target < 0) return;
        var m = mass();
        double s = m.s() / 2, w = m.w() / 2;
        setMass(s, w);                                  // keep half
        net.send(target, new Message.Share(id, s, w));  // push half
    }

    @Override
    protected void onMessage(Message m) {
        if (m instanceof Message.Share(var from, var s, var w)) {
            var cur = mass();
            setMass(cur.s() + s, cur.w() + w);
        }
    }
}