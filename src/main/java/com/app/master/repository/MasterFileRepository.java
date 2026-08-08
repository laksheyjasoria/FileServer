package com.app.master.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.master.entity.MasterFile;

public interface MasterFileRepository
        extends JpaRepository<MasterFile, String> {

    List<MasterFile> findByUserId(String userId);

    List<MasterFile> findByUserIdAndParentIdIsNull(String userId);

    List<MasterFile> findByUserIdAndParentId(String userId, String parentId);

    java.util.Optional<MasterFile> findByIdAndUserId(String id, String userId);

    Long countByUserId(String userId);
}
