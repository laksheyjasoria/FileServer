package com.app.upload.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Service
public class CancelService {

    private final UploadJobRepository repo;

    public CancelService(UploadJobRepository repo) {
        this.repo = repo;
    }

    public void cancel(String uploadId) {

        UploadJob job = repo.findById(uploadId)
                .orElseThrow(UploadNotFoundException::new);

        job.setStatus(UploadStatus.CANCELLED);

        repo.save(job);
    }
}