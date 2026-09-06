package com.app.transfer;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Service;

import com.app.master.entity.MasterFile;
import com.app.storage.factory.StorageFactory;
import com.app.upload.service.UploadChunkReader;

@Service
public class FileTransferService {

	private final ChunkRangeService chunkRangeService;
	private final UploadChunkReader uploadChunkReader;
	private final StorageFactory storageFactory;

	public FileTransferService(ChunkRangeService chunkRangeService, UploadChunkReader uploadChunkReader,
			StorageFactory storageFactory) {

		this.chunkRangeService = chunkRangeService;
		this.uploadChunkReader = uploadChunkReader;
		this.storageFactory = storageFactory;
	}

	public boolean isChunked(MasterFile file) {

		return file != null && file.getUploadJobId() != null && !file.getUploadJobId().isBlank();
	}

	public boolean isLegacy(MasterFile file) {

		return file != null && !isChunked(file) && file.getFileId() != null && !file.getFileId().isBlank();
	}

	/**
	 * Streams the requested logical byte range.
	 *
	 * For chunked files, only the required Telegram chunks are loaded.
	 *
	 * For legacy files, the existing storage abstraction is used and the required
	 * range is copied from the returned byte array.
	 */
	public void streamRange(MasterFile file, ByteRange range, OutputStream outputStream) throws IOException {

		if (file == null) {
			throw new IllegalArgumentException("File cannot be null");
		}

		if (range == null) {
			throw new IllegalArgumentException("Range cannot be null");
		}

		if (outputStream == null) {
			throw new IllegalArgumentException("Output stream cannot be null");
		}

		if (isChunked(file)) {
			streamChunkedRange(file, range, outputStream);
			return;
		}

		if (isLegacy(file)) {
			streamLegacyRange(file, range, outputStream);
			return;
		}

		throw new IllegalArgumentException("File has no valid storage reference: " + file.getId());
	}

	/**
	 * Streams a logical range from chunked storage.
	 */
	private void streamChunkedRange(MasterFile file, ByteRange range, OutputStream outputStream) throws IOException {

		ChunkRangeService.ResolvedChunkRange resolved = chunkRangeService.resolve(file,
				RangeRequest.startEnd(range.getStart(), range.getEnd()));

		long totalWritten = 0L;

		for (ChunkRangeService.ChunkRange chunk : resolved.getChunks()) {

			byte[] chunkData = uploadChunkReader.read(file.getUploadJobId(), chunk.getChunkIndex());

			if (chunkData == null) {
				throw new IOException("Chunk data is null for chunk " + chunk.getChunkIndex());
			}

			long localStart = chunk.getLocalStart();
			long localEnd = chunk.getLocalEnd();

			if (localStart < 0 || localEnd < localStart || localEnd >= chunkData.length) {

				throw new IOException("Invalid local chunk range for chunk " + chunk.getChunkIndex() + ": " + localStart
						+ "-" + localEnd + " / " + chunkData.length);
			}

			int length = (int) (localEnd - localStart + 1L);

			outputStream.write(chunkData, (int) localStart, length);

			totalWritten += length;
		}

		if (totalWritten != range.getLength()) {
			throw new IOException(
					"Transferred byte count mismatch. Expected " + range.getLength() + " but wrote " + totalWritten);
		}

		outputStream.flush();
	}

	/**
	 * Streams a legacy Telegram file range.
	 *
	 * Legacy storage still exposes byte[] through the existing storage abstraction.
	 * We preserve that behavior for backward compatibility.
	 */
	private void streamLegacyRange(MasterFile file, ByteRange range, OutputStream outputStream) throws IOException {

		byte[] content = storageFactory.get().download(file.getFileId());

		if (content == null) {
			throw new IOException("Legacy file content is null");
		}

		long actualFileSize = content.length;

		if (range.getStart() < 0 || range.getEnd() >= actualFileSize || range.getStart() > range.getEnd()) {

			throw new IOException("Requested range is outside legacy file");
		}

		int start = Math.toIntExact(range.getStart());

		int length = Math.toIntExact(range.getLength());

		outputStream.write(content, start, length);

		outputStream.flush();
	}

	/**
	 * Legacy full-file download compatibility method.
	 */
	public byte[] downloadLegacy(String fileId) {

		if (fileId == null || fileId.isBlank()) {
			throw new IllegalArgumentException("File ID cannot be empty");
		}

		return storageFactory.get().download(fileId);
	}
}