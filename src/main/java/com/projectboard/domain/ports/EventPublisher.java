package com.projectboard.domain.ports;

import com.projectboard.domain.events.DomainEvent;

public interface EventPublisher {
    void publish(DomainEvent event);
}
