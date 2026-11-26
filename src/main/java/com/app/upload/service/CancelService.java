package com.app.upload.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Service
public class CancelService {

	private final UploadJobRepository repo;

	public CancelService(UploadJobRepository repo) {
		this.repo = repo;
	}

	@Transactional
	public void cancel(String uploadId, String userId) {

		UploadJob job = repo.findByIdAndUserId(uploadId, userId).orElseThrow(UploadNotFoundException::new);

		if (job.getStatus() == UploadStatus.CANCELLED) {
			return;
		}

		if (job.getStatus() == UploadStatus.COMPLETED) {
			throw new IllegalStateException("Completed uploads cannot be cancelled.");
		}

		job.setStatus(UploadStatus.CANCELLED);

		repo.save(job);
	}
}