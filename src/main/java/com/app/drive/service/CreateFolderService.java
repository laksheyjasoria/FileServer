package com.app.drive.service;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class CreateFolderService {

    private final MasterFileRepository repo;

    public CreateFolderService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public MasterFile create(String userId, String name, String parentId) {
        MasterFile folder = new MasterFile();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setDriveType("FOLDER");
        folder.setAccessType("PUBLIC");
        folder.setSize(0L);
        return repo.save(folder);
    }
}
