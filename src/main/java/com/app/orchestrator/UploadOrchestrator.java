package com.app.orchestrator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadJobRepository;
import com.app.upload.service.ChunkSizeCalculator;

@Component
public class UploadOrchestrator {

	private final UploadJobRepository jobRepo;
	private final MasterFileRepository masterFileRepo;
	private final ChunkSizeCalculator chunkSizeCalculator;

	public UploadOrchestrator(UploadJobRepository jobRepo, MasterFileRepository masterFileRepo,
			ChunkSizeCalculator chunkSizeCalculator) {

		this.jobRepo = jobRepo;
		this.masterFileRepo = masterFileRepo;
		this.chunkSizeCalculator = chunkSizeCalculator;
	}

	@Transactional
	public UploadJob create(CreateUploadRequest request, String userId) {

		validateRequest(request);

		long totalSize = request.getTotalSize();

		long chunkSize = chunkSizeCalculator.calculateChunkSize(totalSize);

		int totalChunks = chunkSizeCalculator.calculateTotalChunks(totalSize, chunkSize);

		/*
		 * Backward compatibility:
		 *
		 * If the old frontend still sends totalChunks, validate it rather than trusting
		 * it.
		 */
		if (request.getTotalChunks() != null && request.getTotalChunks() != totalChunks) {

			throw new IllegalArgumentException("Client supplied totalChunks=" + request.getTotalChunks()
					+ " but server calculated " + totalChunks + ".");
		}

		UploadJob job = new UploadJob();

		job.setUserId(userId);
		job.setFileName(request.getFileName());
		job.setTotalSize(totalSize);
		job.setChunkSize((int) chunkSize);
		job.setTotalChunks(totalChunks);
		job.setUploadedChunks(0);
		job.setUploadedBytes(0L);

		UploadJob savedJob = jobRepo.save(job);

		/*
		 * Phase-1 MasterFile association.
		 *
		 * Incomplete files remain inactive until every chunk has been acknowledged.
		 */
		MasterFile masterFile = new MasterFile();

		masterFile.setUserId(userId);
		masterFile.setName(request.getFileName());
		masterFile.setUploadJobId(savedJob.getId());
		masterFile.setSize(totalSize);
		masterFile.setContentType(request.getContentType());

		masterFile.setParentId(request.getParentId());

		masterFile.setDriveType("FILE");
		masterFile.setActive(false);

		masterFileRepo.save(masterFile);

		return savedJob;
	}

	private void validateRequest(CreateUploadRequest request) {

		if (request == null) {
			throw new IllegalArgumentException("Upload request cannot be null.");
		}

		if (request.getFileName() == null || request.getFileName().isBlank()) {

			throw new IllegalArgumentException("File name is required.");
		}

		if (request.getTotalSize() == null || request.getTotalSize() < 0) {

			throw new IllegalArgumentException("File size must be zero or greater.");
		}
	}
}