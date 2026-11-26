package edu.sjsu.cs262.gossip.net;

/** All gossip messages. Sealed, so a switch over Message is checked for exhaustiveness. */
public sealed interface Message {

    int from();

    /** Push-sum: half of the sender's (s, w) mass. */
    record Share(int from, double s, double w) implements Message {}

    /** Pairwise: sender proposes an exchange, carrying its current value. seq matches replies to requests. */
    record Request(int from, int seq, double value, double momentum) implements Message {}

    /** Pairwise: receiver answers with the value the initiator should adopt (the average, in plain pairwise). */
    record Reply(int from, int seq, double newValue) implements Message {}

    /** Pairwise: receiver is mid-exchange itself and declines. */
    record Busy(int from, int seq) implements Message {}
}