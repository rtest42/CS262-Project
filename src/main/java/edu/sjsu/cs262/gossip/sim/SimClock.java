package edu.sjsu.cs262.gossip.sim;

import java.util.random.RandomGenerator;

/** meanTickNanos is the mean time between a node's clock ticks (Boyd's asynchronous model). **/
public record SimClock(long meanTickNanos) {

    /** Exponentially distributed delay until the next tick. **/
    public long nextDelayNanos(RandomGenerator rng) {
        return (long)(-Math.log(1.0 - rng.nextDouble()) * meanTickNanos);
    }
}
