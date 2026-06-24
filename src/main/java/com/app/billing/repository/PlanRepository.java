package com.app.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.billing.entity.Plan;

public interface PlanRepository
        extends JpaRepository<Plan, String> {

    Optional<Plan> findByName(String name);
}