package com.app.scheduler.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Component
public class UploadCleanupJob {

    private final UploadJobRepository repo;

    public UploadCleanupJob(UploadJobRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanup() {

        List<UploadJob> uploads =
                repo.findByStatus(UploadStatus.IN_PROGRESS);

        for (UploadJob upload : uploads) {

            if (upload.getCreatedAt()
                    .isBefore(LocalDateTime.now().minusHours(12))) {

                upload.setStatus(UploadStatus.FAILED);

                repo.save(upload);
            }
        }
    }
}