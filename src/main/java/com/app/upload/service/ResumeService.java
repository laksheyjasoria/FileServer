package com.app.upload.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ResumeService {

    private final UploadJobRepository jobRepo;
    private final UploadChunkRepository chunkRepo;

    public ResumeService(UploadJobRepository jobRepo,
                         UploadChunkRepository chunkRepo) {
        this.jobRepo = jobRepo;
        this.chunkRepo = chunkRepo;
    }

    public List<UploadChunk> getUploadedChunks(String uploadId) {

        UploadJob job = jobRepo.findById(uploadId)
                .orElseThrow(UploadNotFoundException::new);

        return chunkRepo.findByUploadJobIdOrderByChunkIndexAsc(job.getId());
    }
}