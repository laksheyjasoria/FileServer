package com.app.upload.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.app.orchestrator.UploadOrchestrator;
import com.app.upload.dto.ChunkUploadResponse;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.service.CancelService;
import com.app.upload.service.ChunkService;
import com.app.upload.service.ResumeService;
import com.app.upload.service.UploadStatusService;

@RestController
@RequestMapping("/chunk-upload")
public class ChunkUploadController {

    private final UploadOrchestrator orchestrator;
    private final ChunkService chunkService;
    private final ResumeService resumeService;
    private final CancelService cancelService;
    private final UploadStatusService statusService;

    public ChunkUploadController(UploadOrchestrator orchestrator,
                                 ChunkService chunkService,
                                 ResumeService resumeService,
                                 CancelService cancelService,
                                 UploadStatusService statusService) {
        this.orchestrator = orchestrator;
        this.chunkService = chunkService;
        this.resumeService = resumeService;
        this.cancelService = cancelService;
        this.statusService = statusService;
    }

    @PostMapping("/create")
    public UploadJob create(@RequestBody CreateUploadRequest request,
                            Authentication auth) {

        return orchestrator.create(request, auth.getName());
    }

    @PostMapping("/{uploadId}/{chunkIndex}")
    public void uploadChunk(@PathVariable String uploadId,
                            @PathVariable Integer chunkIndex,
                            @RequestParam MultipartFile file) {

        chunkService.uploadChunk(uploadId, chunkIndex, file);
    }

    @GetMapping("/{uploadId}/resume")
    public List<UploadChunk> resume(@PathVariable String uploadId) {
        return resumeService.getUploadedChunks(uploadId);
    }

    @GetMapping("/{uploadId}/status")
    public ChunkUploadResponse status(@PathVariable String uploadId) {
        return statusService.getStatus(uploadId);
    }

    @DeleteMapping("/{uploadId}")
    public void cancel(@PathVariable String uploadId) {
        cancelService.cancel(uploadId);
    }
}