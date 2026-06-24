package com.app.drive.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class DownloadService {

    private final MasterFileRepository repo;

    public DownloadService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public MasterFile get(String id) {

        return repo.findById(id)
                .orElseThrow(FileNotFoundException::new);
    }
}