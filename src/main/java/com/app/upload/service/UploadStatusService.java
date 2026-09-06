package com.app.upload.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.dto.ChunkUploadResponse;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;

@Service
public class UploadStatusService {

	private final UploadJobRepository repo;

	public UploadStatusService(UploadJobRepository repo) {
		this.repo = repo;
	}

	public ChunkUploadResponse getStatus(String uploadId, String userId) {

		UploadJob job = repo.findByIdAndUserId(uploadId, userId).orElseThrow(UploadNotFoundException::new);

		double progress = 0.0;

		if (job.getTotalSize() != null && job.getTotalSize() > 0) {

			progress = (job.getUploadedBytes() * 100.0) / job.getTotalSize();
		}

		return new ChunkUploadResponse(job.getId(), job.getFileName(), job.getTotalSize(), job.getChunkSize(),
				job.getUploadedChunks(), job.getTotalChunks(), job.getUploadedBytes(), progress,
				job.getStatus().name());
	}
}