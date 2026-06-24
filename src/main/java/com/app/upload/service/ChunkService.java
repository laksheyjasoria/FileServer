package com.app.upload.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.UploadNotFoundException;
import com.app.storage.factory.StorageFactory;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ChunkService {

    private final UploadJobRepository jobRepo;
    private final UploadChunkRepository chunkRepo;
    private final StorageFactory storageFactory;

    public ChunkService(UploadJobRepository jobRepo,
                        UploadChunkRepository chunkRepo,
                        StorageFactory storageFactory) {
        this.jobRepo = jobRepo;
        this.chunkRepo = chunkRepo;
        this.storageFactory = storageFactory;
    }

    public void uploadChunk(String uploadId,
                            Integer chunkIndex,
                            MultipartFile file) {

        UploadJob job = jobRepo.findById(uploadId)
                .orElseThrow(UploadNotFoundException::new);

        if (chunkRepo.existsByUploadJobIdAndChunkIndex(uploadId, chunkIndex)) {
            return;
        }

        String telegramFileId =
                storageFactory.get().upload(file);

        UploadChunk chunk = new UploadChunk();
        chunk.setUploadJobId(uploadId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setTelegramFileId(telegramFileId);
        chunk.setSize(file.getSize());

        chunkRepo.save(chunk);

        job.setUploadedChunks(job.getUploadedChunks() + 1);

        if (job.getUploadedChunks().equals(job.getTotalChunks())) {
            job.setStatus(UploadStatus.COMPLETED);
        }

        jobRepo.save(job);
    }
}