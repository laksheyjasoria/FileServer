package com.app.drive.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class DriveService {

    private final MasterFileRepository repo;

    public DriveService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public List<MasterFile> list(String userId) {
        return repo.findByUserId(userId);
    }
}