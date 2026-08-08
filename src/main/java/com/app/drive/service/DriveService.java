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

    public List<MasterFile> listRoot(String userId) {
        List<MasterFile> files = repo.findByUserIdAndParentIdIsNull(userId);
        for (MasterFile file : files) {
            if ("FOLDER".equals(file.getDriveType())) {
                Long count = repo.countByParentId(file.getId());
                file.setChildrenCount(count.intValue()); // convert Long to int
            }
        }
        return files;
    }

    public List<MasterFile> listContents(String userId, String parentId) {
        requireOwned(parentId, userId);
        List<MasterFile> files = repo.findByUserIdAndParentId(userId, parentId);
        for (MasterFile file : files) {
            if ("FOLDER".equals(file.getDriveType())) {
                Long count = repo.countByParentId(file.getId());
                file.setChildrenCount(count.intValue());
            }
        }
        return files;
    }

    public MasterFile get(String userId, String id) {
        return requireOwned(id, userId);
    }

    private MasterFile requireOwned(String id, String userId) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(com.app.core.exception.FileNotFoundException::new);
    }
}
