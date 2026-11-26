package com.app.upload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;

public interface UploadJobRepository extends JpaRepository<UploadJob, String> {

	List<UploadJob> findByUserId(String userId);

	List<UploadJob> findByStatus(UploadStatus status);

	Optional<UploadJob> findByIdAndUserId(String id, String userId);
}