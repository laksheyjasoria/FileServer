package com.app.upload.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.dto.ChunkUploadResponse;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;

@Service
public class UploadStatusService {

    private final UploadJobRepository repo;

    public UploadStatusService(UploadJobRepository repo) {
        this.repo = repo;
    }

    public ChunkUploadResponse getStatus(String uploadId) {

        UploadJob job = repo.findById(uploadId)
                .orElseThrow(UploadNotFoundException::new);

        return new ChunkUploadResponse(
                job.getId(),
                job.getUploadedChunks(),
                job.getTotalChunks(),
                job.getStatus().name()
        );
    }
}