package com.orbit.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID> {

    long countByEmailAndEventType(String email, AuthEvent.Type eventType);
}
