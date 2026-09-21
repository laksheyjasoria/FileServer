package com.app.upload.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.core.exception.UploadNotFoundException;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ResumeService {

	private final UploadJobRepository jobRepo;
	private final ChunkUploadStateService stateService;

	public ResumeService(UploadJobRepository jobRepo, ChunkUploadStateService stateService) {

		this.jobRepo = jobRepo;
		this.stateService = stateService;
	}

//    @Transactional
//    public List<Integer> resume(String uploadId, String userId) {
//
//        UploadJob job = jobRepo.findByIdAndUserId(uploadId, userId)
//                .orElseThrow(UploadNotFoundException::new);
//
//        if (job.getStatus() == UploadStatus.CANCELLED) {
//            throw new IllegalStateException("Upload has been cancelled.");
//        }
//
//        if (job.getStatus() == UploadStatus.PAUSED
//                || job.getStatus() == UploadStatus.FAILED) {
//            job.setStatus(UploadStatus.IN_PROGRESS);
//            jobRepo.save(job);
//        }
//
//        return stateService.completedChunkIndexes(uploadId, userId);
//    }

	@Transactional
	public List<Integer> resume(String uploadId, String userId) {
		UploadJob job = jobRepo.findByIdAndUserId(uploadId, userId).orElseThrow(UploadNotFoundException::new);
		UploadStatus status = job.getStatus();
		if (status == UploadStatus.CANCELLED) {
			throw new IllegalStateException("Upload has been cancelled.");
		}
		if (status == UploadStatus.PAUSED || status == UploadStatus.FAILED) {
			job.setStatus(UploadStatus.IN_PROGRESS);
			jobRepo.save(job);
		}
		return stateService.completedChunkIndexes(uploadId, userId);
	}
}
