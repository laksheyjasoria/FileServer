package com.app.upload.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.UploadNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.storage.factory.StorageFactory;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadChunkStatus;
import com.app.upload.entity.UploadJob;
import com.app.upload.entity.UploadStatus;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ChunkService {

	private final UploadJobRepository jobRepo;
	private final UploadChunkRepository chunkRepo;
	private final StorageFactory storageFactory;
	private final MasterFileRepository masterFileRepository;

	public ChunkService(UploadJobRepository jobRepo, UploadChunkRepository chunkRepo, StorageFactory storageFactory,
			MasterFileRepository masterFileRepository) {

		this.jobRepo = jobRepo;
		this.chunkRepo = chunkRepo;
		this.storageFactory = storageFactory;
		this.masterFileRepository = masterFileRepository;
	}

	@Transactional
	public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file, String userId) {

		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Chunk file cannot be empty.");
		}

		if (chunkIndex == null || chunkIndex < 0) {
			throw new IllegalArgumentException("Chunk index must be zero or greater.");
		}

		UploadJob job = jobRepo.findByIdAndUserId(uploadId, userId).orElseThrow(UploadNotFoundException::new);

		validateUploadState(job);
		validateChunkIndex(job, chunkIndex);
		validateChunkSize(job, chunkIndex, file.getSize());

		/*
		 * Fast idempotency path.
		 *
		 * The database unique constraint is still required because two simultaneous
		 * requests can both reach this point.
		 */
		UploadChunk existing = chunkRepo.findByUploadJobIdAndChunkIndex(uploadId, chunkIndex).orElse(null);

		if (existing != null && existing.getStatus() == UploadChunkStatus.COMPLETED) {

			return;
		}

		/*
		 * Telegram upload happens outside the database mutation.
		 *
		 * If Telegram fails, no progress is counted.
		 */
		String telegramFileId;

		try {
			telegramFileId = storageFactory.get().upload(file);
		} catch (RuntimeException ex) {

			if (existing != null) {
				existing.setStatus(UploadChunkStatus.FAILED);
				chunkRepo.save(existing);
			}

			throw ex;
		}

		/*
		 * Re-check because another request may have completed the same chunk while this
		 * request was uploading to Telegram.
		 */
		UploadChunk completed = chunkRepo.findByUploadJobIdAndChunkIndex(uploadId, chunkIndex).orElse(null);

		if (completed != null && completed.getStatus() == UploadChunkStatus.COMPLETED) {

			/*
			 * The Telegram object created by this duplicate request is not referenced by
			 * the database.
			 *
			 * We deliberately do not alter progress.
			 */
			return;
		}

		UploadChunk chunk;

		if (completed == null) {
			chunk = new UploadChunk();

			chunk.setUploadJobId(uploadId);
			chunk.setChunkIndex(chunkIndex);
		} else {
			chunk = completed;
		}

		chunk.setTelegramFileId(telegramFileId);
		chunk.setSize(file.getSize());
		chunk.setStatus(UploadChunkStatus.COMPLETED);

		chunkRepo.save(chunk);

		/*
		 * IMPORTANT:
		 *
		 * uploadedBytes and uploadedChunks are calculated from completed chunk records
		 * instead of blindly incrementing based on the incoming request.
		 *
		 * This prevents duplicate requests from double counting.
		 */
		long uploadedBytes = chunkRepo.findByUploadJobIdOrderByChunkIndexAsc(uploadId).stream()
				.filter(c -> c.getStatus() == UploadChunkStatus.COMPLETED).mapToLong(UploadChunk::getSize).sum();

		int uploadedChunks = (int) chunkRepo.findByUploadJobIdOrderByChunkIndexAsc(uploadId).stream()
				.filter(c -> c.getStatus() == UploadChunkStatus.COMPLETED).count();

		job.setUploadedBytes(uploadedBytes);
		job.setUploadedChunks(uploadedChunks);

		if (uploadedChunks == job.getTotalChunks()) {

			if (uploadedBytes != job.getTotalSize()) {
				throw new IllegalStateException("Uploaded bytes do not match total file size.");
			}

			job.setUploadedChunks(uploadedChunks);
			job.setUploadedBytes(uploadedBytes);
			job.setStatus(UploadStatus.COMPLETED);

			jobRepo.save(job);

			MasterFile masterFile = masterFileRepository.findByUploadJobId(job.getId())
					.orElseThrow(() -> new IllegalStateException("MasterFile not found for upload: " + job.getId()));

			masterFile.setActive(true);

			masterFileRepository.save(masterFile);
		}

		jobRepo.save(job);
	}

	private void validateUploadState(UploadJob job) {

		if (job.getStatus() == UploadStatus.CANCELLED) {
			throw new IllegalStateException("Upload has been cancelled.");
		}

		if (job.getStatus() == UploadStatus.COMPLETED) {
			throw new IllegalStateException("Upload has already completed.");
		}

		if (job.getStatus() == UploadStatus.PAUSED) {
			throw new IllegalStateException("Upload is paused.");
		}

		if (job.getStatus() == UploadStatus.FAILED) {
			throw new IllegalStateException("Upload has failed. Resume or retry the upload.");
		}
	}

	private void validateChunkIndex(UploadJob job, Integer chunkIndex) {

		if (chunkIndex >= job.getTotalChunks()) {
			throw new IllegalArgumentException(
					"Chunk index " + chunkIndex + " is outside the valid range 0.." + (job.getTotalChunks() - 1));
		}
	}

	private void validateChunkSize(UploadJob job, Integer chunkIndex, long actualSize) {

		long expectedSize = expectedChunkSize(job, chunkIndex);

		if (actualSize != expectedSize) {
			throw new IllegalArgumentException(
					"Invalid chunk size. Expected " + expectedSize + " bytes but received " + actualSize + " bytes.");
		}
	}

	private long expectedChunkSize(UploadJob job, Integer chunkIndex) {

		long chunkSize = job.getChunkSize();
		long totalSize = job.getTotalSize();

		long start = chunkIndex * chunkSize;

		long remaining = totalSize - start;

		return Math.min(chunkSize, remaining);
	}
}