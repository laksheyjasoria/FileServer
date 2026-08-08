package com.app.drive.service;

import org.springframework.stereotype.Service;

@Service
public class MoveService {
    private final com.app.master.repository.MasterFileRepository repo;

    public MoveService(com.app.master.repository.MasterFileRepository repo) {
        this.repo = repo;
    }

    public void move(String fileId,
            String folderId) {

        com.app.master.entity.MasterFile file = repo.findById(fileId).orElse(null);
        if (file == null)
            return;

        file.setParentId(folderId);
        repo.save(file);
    }
}