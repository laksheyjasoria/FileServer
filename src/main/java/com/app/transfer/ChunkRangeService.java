package com.app.transfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.app.core.exception.UploadNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@Service
public class ChunkRangeService {

	private final UploadJobRepository uploadJobRepository;
	private final UploadChunkRepository uploadChunkRepository;

	public ChunkRangeService(UploadJobRepository uploadJobRepository, UploadChunkRepository uploadChunkRepository) {

		this.uploadJobRepository = uploadJobRepository;
		this.uploadChunkRepository = uploadChunkRepository;
	}

	/**
	 * Resolves an HTTP-style logical range against a chunked MasterFile.
	 *
	 * This method only performs range calculation. It does not download Telegram
	 * data.
	 */
	public ByteRange resolveRange(MasterFile masterFile, RangeRequest rangeRequest) {

		validateMasterFile(masterFile);

		if (rangeRequest == null) {
			throw new IllegalArgumentException("Range request cannot be null.");
		}

		long fileSize = getFileSize(masterFile);

		if (fileSize == 0) {
			throw new IllegalArgumentException("Cannot resolve a range for an empty file.");
		}

		switch (rangeRequest.getType()) {

		case FULL:
			return new ByteRange(0, fileSize - 1);

		case START_END:
			return resolveStartEnd(rangeRequest.getStart(), rangeRequest.getEnd(), fileSize);

		case START_ONLY:
			return resolveStartOnly(rangeRequest.getStart(), fileSize);

		case SUFFIX:
			return resolveSuffix(rangeRequest.getSuffixLength(), fileSize);

		default:
			throw new IllegalArgumentException("Unsupported range type.");
		}
	}

	/**
	 * Maps a logical file range into the physical Telegram chunks that contain that
	 * range.
	 */
	public List<ChunkRange> mapToChunks(MasterFile masterFile, ByteRange range) {

		validateMasterFile(masterFile);

		if (range == null) {
			throw new IllegalArgumentException("Byte range cannot be null.");
		}

		long fileSize = getFileSize(masterFile);

		if (range.getStart() >= fileSize) {
			throw new IllegalArgumentException("Range start is outside the file.");
		}

		if (range.getEnd() >= fileSize) {
			throw new IllegalArgumentException("Range end is outside the file.");
		}

		UploadJob uploadJob = getUploadJob(masterFile);

		int chunkSize = getChunkSize(uploadJob);
		int totalChunks = getTotalChunks(uploadJob);

		int firstChunkIndex = calculateChunkIndex(range.getStart(), chunkSize, totalChunks);

		int lastChunkIndex = calculateChunkIndex(range.getEnd(), chunkSize, totalChunks);

		List<ChunkRange> result = new ArrayList<>();

		for (int chunkIndex = firstChunkIndex; chunkIndex <= lastChunkIndex; chunkIndex++) {

			UploadChunk chunk = uploadChunkRepository.findByUploadJobIdAndChunkIndex(uploadJob.getId(), chunkIndex)
					.orElseThrow(UploadNotFoundException::new);

			validateChunkMetadata(chunk, chunkIndex, chunkSize, fileSize);

			long chunkStart = calculateChunkStart(chunkIndex, chunkSize);

			long chunkEnd = calculateChunkEnd(chunkIndex, chunkSize, fileSize);

			long segmentStart = Math.max(range.getStart(), chunkStart);

			long segmentEnd = Math.min(range.getEnd(), chunkEnd);

			if (segmentStart > segmentEnd) {
				continue;
			}

			int localStart = safeLongToInt(segmentStart - chunkStart);

			int localEnd = safeLongToInt(segmentEnd - chunkStart);

			result.add(new ChunkRange(chunkIndex, chunk.getId(), chunk.getTelegramFileId(), chunkStart, chunkEnd,
					localStart, localEnd));
		}

		if (result.isEmpty()) {
			throw new IllegalStateException("Unable to map requested range to upload chunks.");
		}

		return Collections.unmodifiableList(result);
	}

	/**
	 * Convenience method: resolves an HTTP range and immediately maps it to chunks.
	 */
	public ResolvedChunkRange resolve(MasterFile masterFile, RangeRequest rangeRequest) {

		ByteRange range = resolveRange(masterFile, rangeRequest);

		List<ChunkRange> chunks = mapToChunks(masterFile, range);

		return new ResolvedChunkRange(range, chunks);
	}

	private ByteRange resolveStartEnd(long start, long end, long fileSize) {

		if (start >= fileSize) {
			throw new IllegalArgumentException("Range start is outside the file.");
		}

		long resolvedEnd = Math.min(end, fileSize - 1);

		if (resolvedEnd < start) {
			throw new IllegalArgumentException("Invalid byte range.");
		}

		return new ByteRange(start, resolvedEnd);
	}

	private ByteRange resolveStartOnly(long start, long fileSize) {

		if (start >= fileSize) {
			throw new IllegalArgumentException("Range start is outside the file.");
		}

		return new ByteRange(start, fileSize - 1);
	}

	private ByteRange resolveSuffix(long suffixLength, long fileSize) {

		if (suffixLength <= 0) {
			throw new IllegalArgumentException("Suffix length must be greater than zero.");
		}

		long actualLength = Math.min(suffixLength, fileSize);

		long start = fileSize - actualLength;

		return new ByteRange(start, fileSize - 1);
	}

	private UploadJob getUploadJob(MasterFile masterFile) {

		String uploadJobId = masterFile.getUploadJobId();

		if (uploadJobId == null || uploadJobId.isBlank()) {

			throw new IllegalArgumentException("MasterFile is not backed by a chunked upload.");
		}

		return uploadJobRepository.findById(uploadJobId).orElseThrow(UploadNotFoundException::new);
	}

	private long getFileSize(MasterFile masterFile) {

		Long size = masterFile.getSize();

		if (size == null || size < 0) {
			throw new IllegalStateException("MasterFile has an invalid file size.");
		}

		return size;
	}

	private int getChunkSize(UploadJob uploadJob) {

		Integer chunkSize = uploadJob.getChunkSize();

		if (chunkSize == null || chunkSize <= 0) {

			throw new IllegalStateException("UploadJob has an invalid chunk size.");
		}

		return chunkSize;
	}

	private int getTotalChunks(UploadJob uploadJob) {

		Integer totalChunks = uploadJob.getTotalChunks();

		if (totalChunks == null || totalChunks <= 0) {

			throw new IllegalStateException("UploadJob has an invalid total chunk count.");
		}

		return totalChunks;
	}

	private int calculateChunkIndex(long absolutePosition, int chunkSize, int totalChunks) {

		long index = absolutePosition / chunkSize;

		if (index < 0 || index >= totalChunks) {
			throw new IllegalStateException("Calculated chunk index is invalid.");
		}

		return safeLongToInt(index);
	}

	private long calculateChunkStart(int chunkIndex, int chunkSize) {

		return (long) chunkIndex * chunkSize;
	}

	private long calculateChunkEnd(int chunkIndex, int chunkSize, long fileSize) {

		long start = calculateChunkStart(chunkIndex, chunkSize);

		long theoreticalEnd = start + chunkSize - 1;

		return Math.min(theoreticalEnd, fileSize - 1);
	}

	private void validateChunkMetadata(UploadChunk chunk, int expectedIndex, int chunkSize, long fileSize) {

		if (chunk == null) {
			throw new IllegalStateException("Upload chunk cannot be null.");
		}

		if (chunk.getChunkIndex() == null || chunk.getChunkIndex() != expectedIndex) {

			throw new IllegalStateException("Upload chunk index does not match requested chunk.");
		}

		if (chunk.getTelegramFileId() == null || chunk.getTelegramFileId().isBlank()) {

			throw new IllegalStateException("Upload chunk has no Telegram storage reference.");
		}

		Long storedSize = chunk.getSize();

		if (storedSize == null || storedSize <= 0) {

			throw new IllegalStateException("Upload chunk has an invalid size.");
		}

		long chunkStart = calculateChunkStart(expectedIndex, chunkSize);

		long expectedMaximum = Math.min((long) chunkSize, fileSize - chunkStart);

		if (storedSize > expectedMaximum) {
			throw new IllegalStateException("Upload chunk is larger than the logical chunk boundary.");
		}
	}

	private void validateMasterFile(MasterFile masterFile) {

		if (masterFile == null) {
			throw new IllegalArgumentException("MasterFile cannot be null.");
		}

		if (masterFile.getUploadJobId() == null || masterFile.getUploadJobId().isBlank()) {

			throw new IllegalArgumentException("MasterFile is not a chunked file.");
		}
	}

	private int safeLongToInt(long value) {

		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {

			throw new IllegalStateException("Value exceeds integer range.");
		}

		return (int) value;
	}

	public static class ChunkRange {

		private final int chunkIndex;
		private final String chunkId;
		private final String telegramFileId;

		private final long chunkStart;
		private final long chunkEnd;

		private final int localStart;
		private final int localEnd;

		public ChunkRange(int chunkIndex, String chunkId, String telegramFileId, long chunkStart, long chunkEnd,
				int localStart, int localEnd) {

			this.chunkIndex = chunkIndex;
			this.chunkId = chunkId;
			this.telegramFileId = telegramFileId;
			this.chunkStart = chunkStart;
			this.chunkEnd = chunkEnd;
			this.localStart = localStart;
			this.localEnd = localEnd;
		}

		public int getChunkIndex() {
			return chunkIndex;
		}

		public String getChunkId() {
			return chunkId;
		}

		public String getTelegramFileId() {
			return telegramFileId;
		}

		public long getChunkStart() {
			return chunkStart;
		}

		public long getChunkEnd() {
			return chunkEnd;
		}

		public int getLocalStart() {
			return localStart;
		}

		public int getLocalEnd() {
			return localEnd;
		}

		public int getLength() {
			return localEnd - localStart + 1;
		}
	}

	public static class ResolvedChunkRange {

		private final ByteRange range;
		private final List<ChunkRange> chunks;

		public ResolvedChunkRange(ByteRange range, List<ChunkRange> chunks) {

			this.range = range;
			this.chunks = chunks;
		}

		public ByteRange getRange() {
			return range;
		}

		public List<ChunkRange> getChunks() {
			return chunks;
		}
	}
}