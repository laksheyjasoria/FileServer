package com.app.upload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.upload.entity.UploadChunk;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, String> {

	List<UploadChunk> findByUploadJobIdOrderByChunkIndexAsc(String uploadJobId);

	Optional<UploadChunk> findByUploadJobIdAndChunkIndex(String uploadJobId, Integer chunkIndex);

	boolean existsByUploadJobIdAndChunkIndex(String uploadJobId, Integer chunkIndex);

	long countByUploadJobIdAndStatus(String uploadJobId, com.app.upload.entity.UploadChunkStatus status);

	long countByUploadJobId(String uploadJobId);
}