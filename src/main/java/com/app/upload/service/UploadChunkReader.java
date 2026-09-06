package com.app.upload.service;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.transfer.telegram.TelegramFileReader;
import com.app.upload.entity.UploadChunk;
import com.app.upload.repository.UploadChunkRepository;

@Service
public class UploadChunkReader {

	private final UploadChunkRepository chunkRepository;
	private final TelegramFileReader telegramFileReader;

	public UploadChunkReader(UploadChunkRepository chunkRepository, TelegramFileReader telegramFileReader) {

		this.chunkRepository = chunkRepository;
		this.telegramFileReader = telegramFileReader;
	}

	/**
	 * Reads one logical upload chunk.
	 *
	 * This method hides Telegram file IDs from the rest of the application.
	 */
	public byte[] read(String uploadId, int chunkIndex) {

		if (uploadId == null || uploadId.isBlank()) {

			throw new IllegalArgumentException("Upload ID cannot be empty.");
		}

		if (chunkIndex < 0) {

			throw new IllegalArgumentException("Chunk index cannot be negative.");
		}

		UploadChunk chunk = chunkRepository.findByUploadJobIdAndChunkIndex(uploadId, chunkIndex)
				.orElseThrow(UploadNotFoundException::new);

		if (chunk.getTelegramFileId() == null || chunk.getTelegramFileId().isBlank()) {

			throw new IllegalStateException("Upload chunk has no Telegram storage reference.");
		}

		byte[] data = telegramFileReader.read(chunk.getTelegramFileId());

		validateChunkSize(chunk, data);

		return data;
	}

	private void validateChunkSize(UploadChunk chunk, byte[] data) {

		if (chunk.getSize() == null) {
			return;
		}

		if (chunk.getSize() != data.length) {

			throw new IllegalStateException("Telegram chunk size does not match database metadata. " + "Expected "
					+ chunk.getSize() + " bytes but received " + data.length + " bytes.");
		}
	}
}