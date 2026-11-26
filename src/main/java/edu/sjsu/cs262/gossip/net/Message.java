package edu.sjsu.cs262.gossip.net;

/** All gossip messages. Sealed, so a switch over Message is checked for exhaustiveness. */
public sealed interface Message {

    int from();

    /** Push-sum: half of the sender's (s, w) mass. */
    record Share(int from, double s, double w) implements Message {}

    /** Pairwise: sender proposes an exchange, carrying its current value. seq matches replies to requests. */
    record Request(int from, int seq, double value, double momentum) implements Message {}

    /**
     * Pairwise: receiver answers with the CHANGE the initiator must apply to its own value (newValue - valueSent).
     * Sending a change instead of an absolute value keeps the sum exact however exchanges interleave,
     * because the receiver has already moved by the opposite amount.
     */
    record Reply(int from, int seq, double delta) implements Message {}

    /** Pairwise: receiver is mid-exchange itself and declines. */
    record Busy(int from, int seq) implements Message {}
}