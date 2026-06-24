package com.app.drive.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.drive.service.DownloadService;
import com.app.master.entity.MasterFile;

@RestController
@RequestMapping("/download")
public class DownloadController {

	private final DownloadService service;

	public DownloadController(DownloadService service) {
		this.service = service;
	}

	@GetMapping("/{id}")
	public MasterFile get(@PathVariable String id) {
		return service.get(id);
	}
}