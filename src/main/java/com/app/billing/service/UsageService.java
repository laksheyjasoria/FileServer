package com.app.billing.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class UsageService {

    private final MasterFileRepository repo;

    public UsageService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public long calculateUsedStorage(String userId) {

        List<MasterFile> files =
                repo.findByUserId(userId);

        return files.stream()
                .mapToLong(MasterFile::getSize)
                .sum();
    }
}