package com.app.logger.api.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.LoggerNotFoundException;
import com.app.logger.api.entity.LoggerEntity;
import com.app.logger.api.repository.LoggerRepository;

@Service
public class LoggerService {

	private final LoggerRepository repo;

	public LoggerService(LoggerRepository repo) {
		this.repo = repo;
	}

	public LoggerEntity create(String name) {

		return repo.findByName(name).orElseGet(() -> {
			LoggerEntity l = new LoggerEntity();
			l.setName(name);
			return repo.save(l);
		});
	}

	public LoggerEntity get(String id) {
		return repo.findById(id).orElseThrow(LoggerNotFoundException::new);
	}

	public LoggerEntity save(LoggerEntity logger) {
		return repo.save(logger);
	}

	public void delete(String id) {
		if (!repo.existsById(id)) {
			throw new LoggerNotFoundException();
		}
		repo.deleteById(id);
	}
}