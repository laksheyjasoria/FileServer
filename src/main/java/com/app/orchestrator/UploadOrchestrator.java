package com.app.orchestrator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;

@Component
public class UploadOrchestrator {

    private final UploadJobRepository uploadJobRepository;
    private final MasterFileRepository masterFileRepository;

    public UploadOrchestrator(
            UploadJobRepository uploadJobRepository,
            MasterFileRepository masterFileRepository) {

        this.uploadJobRepository = uploadJobRepository;
        this.masterFileRepository = masterFileRepository;
    }

    @Transactional
    public UploadJob create(CreateUploadRequest request, String userId) {

        UploadJob job = new UploadJob();

        job.setFileName(request.getFileName());
        job.setTotalSize(request.getTotalSize());
        job.setTotalChunks(request.getTotalChunks());
        job.setUserId(userId);

        UploadJob savedJob = uploadJobRepository.save(job);

        MasterFile masterFile = new MasterFile();

        masterFile.setUserId(userId);
        masterFile.setName(request.getFileName());
        masterFile.setUploadJobId(savedJob.getId());
        masterFile.setSize(request.getTotalSize());
        masterFile.setContentType(request.getContentType());
        masterFile.setParentId(request.getParentId());

        /*
         * This is an incomplete upload session.
         * It must not appear as a completed Drive file until
         * all chunks have been successfully stored.
         */
        masterFile.setDriveType("FILE");
        masterFile.setAccessType("PUBLIC");
        masterFile.setActive(false);

        masterFileRepository.save(masterFile);

        return savedJob;
    }
}