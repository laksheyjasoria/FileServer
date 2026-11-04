package com.app.billing.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Service
public class UsageService {

    private final MasterFileRepository fileRepository;
    private final UploadJobRepository uploadJobRepository;

    public UsageService(
            MasterFileRepository fileRepository,
            UploadJobRepository uploadJobRepository) {
        this.fileRepository = fileRepository;
        this.uploadJobRepository = uploadJobRepository;
    }

    /**
     * Counts active stored files plus unfinished chunked uploads as reservations.
     * Completed chunked uploads are represented by their active MasterFile row and
     * are therefore not counted twice.
     */
    public long calculateUsedStorage(String userId) {
        List<MasterFile> activeFiles = fileRepository.findByUserIdAndActiveTrue(userId);
        long storedBytes = activeFiles.stream()
                .filter(file -> file.getSize() != null)
                .mapToLong(MasterFile::getSize)
                .sum();

        List<UploadJob> reservations = uploadJobRepository.findByUserIdAndStatusIn(
                userId, List.of(UploadStatus.IN_PROGRESS, UploadStatus.PAUSED));
        long reservedBytes = reservations.stream()
                .filter(job -> job.getTotalSize() != null)
                .mapToLong(UploadJob::getTotalSize)
                .sum();

        return Math.addExact(storedBytes, reservedBytes);
    }
}
