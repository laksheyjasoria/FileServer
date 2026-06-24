package com.app.drive.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class DeleteService {

    private final MasterFileRepository repo;

    public DeleteService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public void delete(String id) {

        MasterFile file = repo.findById(id)
                .orElseThrow(FileNotFoundException::new);

        repo.delete(file);
    }
}