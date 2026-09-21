package com.app.orchestrator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.billing.entity.Subscription;
import com.app.billing.service.BillingService;
import com.app.billing.service.UsageService;
import com.app.core.exception.StorageLimitExceededException;
import com.app.core.exception.UploadLimitExceededException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;
import com.app.upload.service.ChunkSizeCalculator;

@Component
public class UploadOrchestrator {

    private final UploadJobRepository jobRepo;
    private final MasterFileRepository masterFileRepo;
    private final ChunkSizeCalculator chunkSizeCalculator;
    private final BillingService billingService;
    private final UsageService usageService;

    public UploadOrchestrator(
            UploadJobRepository jobRepo,
            MasterFileRepository masterFileRepo,
            ChunkSizeCalculator chunkSizeCalculator,
            BillingService billingService,
            UsageService usageService) {

        this.jobRepo = jobRepo;
        this.masterFileRepo = masterFileRepo;
        this.chunkSizeCalculator = chunkSizeCalculator;
        this.billingService = billingService;
        this.usageService = usageService;
    }

    @Transactional
    public UploadJob create(CreateUploadRequest request, String userId,boolean admin) {

    	validateRequest(request);

    	long totalSize = request.getTotalSize();

    	if (!admin) {
    	    Subscription subscription =
    	            billingService.getOrCreateActiveSubscription(userId);

    	    if (totalSize > subscription.getPlan().getMaxUploadSizeBytes()) {
    	        throw new UploadLimitExceededException();
    	    }

    	    long usedStorage = usageService.calculateUsedStorage(userId);

    	    if (usedStorage + totalSize > subscription.getPlan().getStorageLimitBytes()) {
    	        throw new StorageLimitExceededException();
    	    }
    	}

    	System.out.println("UPLOAD totalSize = " + totalSize);
    	System.out.println("UPLOAD totalSize MB = "
    	        + (totalSize / (1024.0 * 1024.0)));

    	long chunkSize =
    	        chunkSizeCalculator.calculateChunkSize(totalSize);

    	System.out.println("CALCULATED chunkSize MB = "
    	        + (chunkSize / (1024.0 * 1024.0)));

    	int totalChunks =
    	        chunkSizeCalculator.calculateTotalChunks(totalSize, chunkSize);

    	System.out.println("CALCULATED totalChunks = " + totalChunks);

        UploadJob job = new UploadJob();
        job.setUserId(userId);
        job.setFileName(request.getFileName());
        job.setTotalSize(totalSize);
        job.setChunkSize((int) chunkSize);
        job.setTotalChunks(totalChunks);
        job.setUploadedChunks(0);
        job.setUploadedBytes(0L);

        /*
         * A zero-byte file has no physical chunk to send to Telegram.
         * Complete it immediately.
         */
        if (totalSize == 0) {
            job.setStatus(UploadStatus.COMPLETED);
        }

        UploadJob savedJob = jobRepo.save(job);

        MasterFile masterFile = new MasterFile();
        masterFile.setUserId(userId);
        masterFile.setName(request.getFileName());
        masterFile.setUploadJobId(savedJob.getId());
        masterFile.setSize(totalSize);
        masterFile.setContentType(request.getContentType());
        masterFile.setParentId(request.getParentId());
        masterFile.setDriveType("FILE");
        masterFile.setAccessType("PUBLIC");
        masterFile.setActive(totalSize == 0);

        masterFileRepo.save(masterFile);

        return savedJob;
    }

    private void validateRequest(CreateUploadRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Upload request cannot be null.");
        }

        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }

        if (request.getTotalSize() == null || request.getTotalSize() < 0) {
            throw new IllegalArgumentException("File size must be zero or greater.");
        }
    }
}
