package com.app.logger.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.logger.api.entity.LoggerEntity;
import com.app.logger.api.service.LogService;
import com.app.logger.api.service.LoggerService;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/logger")
public class LoggerController {

	private final LoggerService loggerService;
	private final LogService logService;

	public LoggerController(LoggerService loggerService, LogService logService) {
		this.loggerService = loggerService;
		this.logService = logService;
	}

	// 🔐 MASTER KEY REQUIRED
	@PostMapping("/create")
	public String create(@RequestParam @NotBlank String name) {

		LoggerEntity logger = loggerService.create(name);
		return logger.getId();
	}

	// 🔐 MASTER KEY REQUIRED
	@PutMapping("/{id}")
	public void update(@PathVariable String id, @RequestParam boolean info, @RequestParam boolean warn,
			@RequestParam boolean debug) {
		LoggerEntity logger = loggerService.get(id);
		logger.setInfoEnabled(info);
		logger.setWarnEnabled(warn);
		logger.setDebugEnabled(debug);
		loggerService.save(logger); // use save method to update
	}

	// 🔐 MASTER KEY REQUIRED
	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		loggerService.delete(id);
	}

	// 🔓 PUBLIC API
	@PostMapping("/log")
	public void log(@RequestParam String loggerId, @RequestParam String level, @RequestParam String message) {

		logService.log(loggerId, level, message);
	}

	// 🔓 PUBLIC API
	@PostMapping("/error")
	public void error(@RequestParam String loggerId, @RequestParam String message) {

		logService.error(loggerId, message);
	}

	@GetMapping("/list")
	public List<LoggerEntity> listAll() {
		return loggerService.getAll(); // implement this in LoggerService
	}
}