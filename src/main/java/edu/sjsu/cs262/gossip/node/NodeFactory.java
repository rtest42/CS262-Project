package edu.sjsu.cs262.gossip.node;

/** Creates a node of the algorithm under test. Experiment builds the initial nodes with it and ChurnController builds joiners. */
@FunctionalInterface
public interface NodeFactory {
    GossipNode create(int id, double x, double y, double value);
}
