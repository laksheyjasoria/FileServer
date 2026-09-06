package com.app.upload.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.app.orchestrator.UploadOrchestrator;
import com.app.upload.dto.ChunkUploadResponse;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.service.CancelService;
import com.app.upload.service.ChunkService;
import com.app.upload.service.PauseService;
import com.app.upload.service.ResumeService;
import com.app.upload.service.UploadStatusService;

@RestController
@RequestMapping("/chunk-upload")
public class ChunkUploadController {

	private final UploadOrchestrator orchestrator;
	private final ChunkService chunkService;
	private final ResumeService resumeService;
	private final PauseService pauseService;
	private final CancelService cancelService;
	private final UploadStatusService statusService;

	public ChunkUploadController(UploadOrchestrator orchestrator, ChunkService chunkService,
			ResumeService resumeService, PauseService pauseService, CancelService cancelService,
			UploadStatusService statusService) {

		this.orchestrator = orchestrator;
		this.chunkService = chunkService;
		this.resumeService = resumeService;
		this.pauseService = pauseService;
		this.cancelService = cancelService;
		this.statusService = statusService;
	}

	@PostMapping("/create")
	public UploadJob create(@RequestBody CreateUploadRequest request, Authentication auth) {

		return orchestrator.create(request, auth.getName());
	}

	@PostMapping("/{uploadId}/{chunkIndex}")
	public void uploadChunk(@PathVariable String uploadId, @PathVariable Integer chunkIndex,
			@RequestParam MultipartFile file, Authentication auth) {

		chunkService.uploadChunk(uploadId, chunkIndex, file, auth.getName());
	}

	@GetMapping("/{uploadId}/resume")
	public List<UploadChunk> resume(@PathVariable String uploadId, Authentication auth) {

		return resumeService.resume(uploadId, auth.getName());
	}

	@GetMapping("/{uploadId}/status")
	public ChunkUploadResponse status(@PathVariable String uploadId, Authentication auth) {

		return statusService.getStatus(uploadId, auth.getName());
	}

	@PostMapping("/{uploadId}/pause")
	public void pause(@PathVariable String uploadId, Authentication auth) {

		pauseService.pause(uploadId, auth.getName());
	}

	@PostMapping("/{uploadId}/resume")
	public List<UploadChunk> resumePost(@PathVariable String uploadId, Authentication auth) {

		return resumeService.resume(uploadId, auth.getName());
	}

	@DeleteMapping("/{uploadId}")
	public void cancel(@PathVariable String uploadId, Authentication auth) {

		cancelService.cancel(uploadId, auth.getName());
	}
}