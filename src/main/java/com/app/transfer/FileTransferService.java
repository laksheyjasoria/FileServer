package com.app.transfer;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Service;

import com.app.core.exception.StorageException;
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

	/**
	 * Streams a requested logical range of a chunked file.
	 *
	 * The complete file is never assembled in memory.
	 */
	public void streamChunkedRange(MasterFile masterFile, ByteRange range, OutputStream outputStream) {

		if (masterFile == null) {
			throw new IllegalArgumentException("MasterFile cannot be null.");
		}

		if (range == null) {
			throw new IllegalArgumentException("Byte range cannot be null.");
		}

		if (outputStream == null) {
			throw new IllegalArgumentException("Output stream cannot be null.");
		}

		ChunkRangeService.ResolvedChunkRange resolved = chunkRangeService.resolve(masterFile,
				RangeRequest.startEnd(range.getStart(), range.getEnd()));

		long totalWritten = 0;

		try {

			for (ChunkRangeService.ChunkRange chunkRange : resolved.getChunks()) {

				byte[] chunkData = uploadChunkReader.read(masterFile.getUploadJobId(), chunkRange.getChunkIndex());

				int localStart = chunkRange.getLocalStart();

				int localEnd = chunkRange.getLocalEnd();

				int length = localEnd - localStart + 1;

				outputStream.write(chunkData, localStart, length);

				totalWritten += length;
			}

			outputStream.flush();

		} catch (IOException ex) {

			throw new StorageException("Failed while streaming file content: " + ex.getMessage());

		} catch (RuntimeException ex) {

			throw ex;

		} catch (Exception ex) {

			throw new StorageException("Failed while streaming file content.");
		}

		if (totalWritten != range.getLength()) {
			throw new IllegalStateException("Transferred byte count does not match requested range. " + "Expected "
					+ range.getLength() + " bytes but transferred " + totalWritten + " bytes.");
		}
	}

	/**
	 * Legacy storage path.
	 *
	 * Existing files that have fileId but no uploadJobId continue using the
	 * existing StorageFactory implementation.
	 */
	public byte[] downloadLegacy(String fileId) {

		if (fileId == null || fileId.isBlank()) {

			throw new IllegalArgumentException("File ID cannot be empty.");
		}

		return storageFactory.get().download(fileId);
	}

	public boolean isChunked(MasterFile masterFile) {

		return masterFile != null && masterFile.getUploadJobId() != null && !masterFile.getUploadJobId().isBlank();
	}

	public boolean isLegacy(MasterFile masterFile) {

		return masterFile != null && !isChunked(masterFile) && masterFile.getFileId() != null
				&& !masterFile.getFileId().isBlank();
	}
}