package com.hit.algorithm;

/**
 * Factory class for creating task assignment strategies by name.
 * Encapsulates the creation logic for switching between strategies at runtime.
 */
public class StrategyFactory {

    /**
     * Returns an assignment strategy implementation based on the given name.
     *
     * @param name the name of the strategy ("greedy" or "balanced")
     * @return the corresponding strategy instance
     * @throws IllegalArgumentException if the strategy name is unknown
     */
    public static ITaskAssignment getStrategy(String name) {
        return switch (name.toLowerCase()) {
            case "greedy" -> new GreedyEarliestFit();
            case "balanced" -> new BalancedLoad();
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }
}
