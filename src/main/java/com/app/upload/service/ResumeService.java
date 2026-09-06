package com.app.upload.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ResumeService {

	private final UploadJobRepository jobRepo;
	private final UploadChunkRepository chunkRepo;

	public ResumeService(UploadJobRepository jobRepo, UploadChunkRepository chunkRepo) {

		this.jobRepo = jobRepo;
		this.chunkRepo = chunkRepo;
	}

	@Transactional
	public List<UploadChunk> resume(String uploadId, String userId) {

		UploadJob job = jobRepo.findByIdAndUserId(uploadId, userId).orElseThrow(UploadNotFoundException::new);

		if (job.getStatus() == UploadStatus.CANCELLED) {
			throw new IllegalStateException("Upload has been cancelled.");
		}

		if (job.getStatus() == UploadStatus.COMPLETED) {
			return chunkRepo.findByUploadJobIdOrderByChunkIndexAsc(uploadId);
		}

		if (job.getStatus() == UploadStatus.PAUSED) {
			job.setStatus(UploadStatus.IN_PROGRESS);
			jobRepo.save(job);
		}

		return chunkRepo.findByUploadJobIdOrderByChunkIndexAsc(uploadId);
	}
}