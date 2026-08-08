package com.app.drive.service;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class RenameService {

    private final MasterFileRepository repo;

    public RenameService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public MasterFile rename(String id, String newName) {
        MasterFile file = repo.findById(id).orElse(null);
        if (file == null)
            return null;
        file.setName(newName);
        return repo.save(file);
    }
}
