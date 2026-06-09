package com.projectboard.infrastructure.notification;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {

    private final int failureThreshold;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile State state = State.CLOSED;
    private volatile Instant openedAt;

    public CircuitBreaker(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public boolean allowRequest() {
        if (state == State.CLOSED) return true;
        // half-open after 30s
        if (openedAt != null && Instant.now().isAfter(openedAt.plusSeconds(30))) {
            state = State.HALF_OPEN;
            return true;
        }
        return state == State.HALF_OPEN;
    }

    public void recordSuccess() {
        failures.set(0);
        state = State.CLOSED;
    }

    public void recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            state = State.OPEN;
            openedAt = Instant.now();
        }
    }

    public State getState() {
        return state;
    }

    enum State { CLOSED, OPEN, HALF_OPEN }
}
