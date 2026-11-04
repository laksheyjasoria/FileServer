package com.app.upload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;

public interface UploadJobRepository extends JpaRepository<UploadJob, String> {

	List<UploadJob> findByUserId(String userId);

	List<UploadJob> findByStatus(UploadStatus status);

	Optional<UploadJob> findByIdAndUserId(String id, String userId);

	/**
	 * Serializes state transitions for one upload across application instances.
	 * Keep Telegram network I/O outside the transaction holding this lock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from UploadJob job where job.id = :id and job.userId = :userId")
	Optional<UploadJob> findByIdAndUserIdForUpdate(
			@Param("id") String id,
			@Param("userId") String userId);
}