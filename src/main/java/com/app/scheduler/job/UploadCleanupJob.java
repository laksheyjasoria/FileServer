package com.app.scheduler.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.scheduler.enabled:true} && ${app.scheduler.upload-cleanup.enabled:true}")
public class UploadCleanupJob {

    private final UploadJobRepository repo;

    public UploadCleanupJob(UploadJobRepository repo) {
        this.repo = repo;
    }

    @Transactional
    @Scheduled(cron = "${app.scheduler.upload-cleanup.cron:0 */30 * * * *}")
    public void cleanup() {
        LocalDateTime staleBefore = LocalDateTime.now().minusHours(12);
        List<UploadJob> uploads = repo.findByStatusIn(
                List.of(UploadStatus.IN_PROGRESS, UploadStatus.PAUSED));

        for (UploadJob candidate : uploads) {
            UploadJob upload = repo.findByIdAndUserIdForUpdate(candidate.getId(), candidate.getUserId())
                    .orElse(null);
            if (upload == null || (upload.getStatus() != UploadStatus.IN_PROGRESS
                    && upload.getStatus() != UploadStatus.PAUSED)) {
                continue;
            }

            LocalDateTime lastUpdated = upload.getUpdatedAt() != null
                    ? upload.getUpdatedAt() : upload.getCreatedAt();
            if (lastUpdated != null && lastUpdated.isBefore(staleBefore)) {
                upload.setStatus(UploadStatus.FAILED);
                repo.save(upload);
            }
        }
    }
}