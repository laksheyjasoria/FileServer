package com.app.upload.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Service
public class PauseService {

    private final UploadJobRepository repo;

    public PauseService(UploadJobRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void pause(
            String uploadId,
            String userId) {

        UploadJob job = repo.findByIdAndUserId(
                uploadId,
                userId)
                .orElseThrow(UploadNotFoundException::new);

        if (job.getStatus() == UploadStatus.CANCELLED) {
            return;
        }

        if (job.getStatus() == UploadStatus.COMPLETED) {
            return;
        }

        if (job.getStatus() == UploadStatus.PAUSED) {
            return;
        }

        if (job.getStatus() != UploadStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Upload cannot be paused from status "
                    + job.getStatus());
        }

        job.setStatus(UploadStatus.PAUSED);

        repo.save(job);
    }
}