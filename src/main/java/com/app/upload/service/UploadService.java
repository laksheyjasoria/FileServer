package com.app.upload.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.billing.entity.Subscription;
import com.app.billing.service.BillingService;
import com.app.billing.service.UsageService;
import com.app.core.exception.StorageLimitExceededException;
import com.app.core.exception.UploadLimitExceededException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.storage.factory.StorageFactory;

@Service
public class UploadService {

    private final StorageFactory factory;
    private final MasterFileRepository repo;

    private final BillingService billingService;
    private final UsageService usageService;

    public UploadService(StorageFactory factory,
                         MasterFileRepository repo,
                         BillingService billingService,
                         UsageService usageService) {
        this.factory = factory;
        this.repo = repo;
        this.billingService = billingService;
        this.usageService = usageService;
    }

    public String upload(MultipartFile file,
                         String userId) {

        Subscription sub =
                billingService.getActiveSubscription(userId);

        if (file.getSize() >
                sub.getPlan().getMaxUploadSizeBytes()) {

            throw new UploadLimitExceededException();
        }

        long used =
                usageService.calculateUsedStorage(userId);

        if ((used + file.getSize()) >
                sub.getPlan().getStorageLimitBytes()) {

            throw new StorageLimitExceededException();
        }

        String telegramFileId =
                factory.get().upload(file);

        MasterFile masterFile = new MasterFile();

        masterFile.setName(file.getOriginalFilename());
        masterFile.setFileId(telegramFileId);
        masterFile.setSize(file.getSize());
        masterFile.setContentType(file.getContentType());
        masterFile.setUserId(userId);

        repo.save(masterFile);

        return masterFile.getId();
    }
}