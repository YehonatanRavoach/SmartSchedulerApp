package com.hit.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe unique ID generator for tasks, team members, etc.
 */
public class UniqueIdGenerator {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public UniqueIdGenerator(String prefix) {
        this.prefix = prefix;
    }

    public String nextId() {
        return prefix + counter.getAndIncrement();
    }
}

