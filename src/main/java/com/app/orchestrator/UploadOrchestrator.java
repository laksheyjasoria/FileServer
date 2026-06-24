package com.app.orchestrator;

import org.springframework.stereotype.Component;

import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;

@Component
public class UploadOrchestrator {

    private final UploadJobRepository repo;

    public UploadOrchestrator(UploadJobRepository repo) {
        this.repo = repo;
    }

    public UploadJob create(CreateUploadRequest request,
                            String userId) {

        UploadJob job = new UploadJob();

        job.setFileName(request.getFileName());
        job.setTotalSize(request.getTotalSize());
        job.setTotalChunks(request.getTotalChunks());
        job.setUserId(userId);

        return repo.save(job);
    }
}