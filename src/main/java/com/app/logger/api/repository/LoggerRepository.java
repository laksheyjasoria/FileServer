package com.app.logger.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.logger.api.entity.LoggerEntity;

public interface LoggerRepository extends JpaRepository<LoggerEntity, String> {

	Optional<LoggerEntity> findByName(String name);
}