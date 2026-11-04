package com.app.billing.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Counts stored files, including recoverable trash, and unfinished chunked
     * uploads as reservations. Failed/cancelled chunked upload placeholders are
     * excluded. Completed chunked files are counted once through MasterFile.
     */
    public long calculateUsedStorage(String userId) {
        Map<String, UploadStatus> uploadStatuses = uploadJobRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UploadJob::getId, UploadJob::getStatus, (first, ignored) -> first));

        List<MasterFile> files = fileRepository.findByUserId(userId);
        long usedBytes = files.stream()
                .filter(file -> file.getSize() != null)
                .filter(file -> {
                    if (file.getUploadJobId() == null) {
                        return true;
                    }
                    UploadStatus status = uploadStatuses.get(file.getUploadJobId());
                    return status == null || status == UploadStatus.COMPLETED
                            || status == UploadStatus.IN_PROGRESS || status == UploadStatus.PAUSED;
                })
                .mapToLong(MasterFile::getSize)
                .sum();

        return usedBytes;
    }
}
