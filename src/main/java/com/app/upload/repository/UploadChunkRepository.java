package com.app.upload.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.upload.entity.UploadChunk;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, String> {

    List<UploadChunk> findByUploadJobIdOrderByChunkIndexAsc(String uploadJobId);

    boolean existsByUploadJobIdAndChunkIndex(String uploadJobId,
                                             Integer chunkIndex);
}