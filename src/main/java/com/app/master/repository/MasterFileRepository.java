package com.app.master.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.master.entity.MasterFile;

public interface MasterFileRepository
        extends JpaRepository<MasterFile, String> {

    List<MasterFile> findByUserId(String userId);

    Long countByUserId(String userId);
}