package com.app.drive.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@Service
public class CopyService {

    private final MasterFileRepository repo;

    public CopyService(MasterFileRepository repo) {
        this.repo = repo;
    }

    public MasterFile copy(String id) {

        MasterFile original = repo.findById(id)
                .orElseThrow(FileNotFoundException::new);

        MasterFile copy = new MasterFile();

        copy.setName(original.getName());
        copy.setFileId(original.getFileId());
        copy.setSize(original.getSize());
        copy.setContentType(original.getContentType());
        copy.setUserId(original.getUserId());

        return repo.save(copy);
    }
}