package com.app.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.master.entity.MasterFile;
import com.app.upload.entity.UploadChunk;
import com.app.upload.entity.UploadJob;
import com.app.upload.repository.UploadChunkRepository;
import com.app.upload.repository.UploadJobRepository;

@ExtendWith(MockitoExtension.class)
class ChunkRangeServiceTest {

	@Mock
	private UploadJobRepository uploadJobRepository;

	@Mock
	private UploadChunkRepository uploadChunkRepository;

	private ChunkRangeService service;

	private MasterFile masterFile;

	@BeforeEach
	void setUp() {
		service = new ChunkRangeService(uploadJobRepository, uploadChunkRepository);

		masterFile = new MasterFile();
		masterFile.setId("file-1");
		masterFile.setUploadJobId("upload-1");
		masterFile.setSize(50L);
	}

	@Test
	void shouldResolveFullFile() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.full());

		assertEquals(0L, range.getStart());
		assertEquals(49L, range.getEnd());
		assertEquals(50L, range.getLength());
	}

	@Test
	void shouldResolveStartEndRange() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.startEnd(5, 15));

		assertEquals(5L, range.getStart());
		assertEquals(15L, range.getEnd());
		assertEquals(11L, range.getLength());
	}

	@Test
	void shouldResolveOpenEndedRange() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.startOnly(30));

		assertEquals(30L, range.getStart());
		assertEquals(49L, range.getEnd());
		assertEquals(20L, range.getLength());
	}

	@Test
	void shouldResolveSuffixRange() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.suffix(10));

		assertEquals(40L, range.getStart());
		assertEquals(49L, range.getEnd());
		assertEquals(10L, range.getLength());
	}

	@Test
	void shouldResolveSuffixLargerThanFile() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.suffix(100));

		assertEquals(0L, range.getStart());
		assertEquals(49L, range.getEnd());
		assertEquals(50L, range.getLength());
	}

	@Test
	void shouldRejectRangeStartingAfterFile() {

		assertThrows(IllegalArgumentException.class,
				() -> service.resolveRange(masterFile, RangeRequest.startOnly(50)));
	}

	@Test
	void shouldRejectNullMasterFile() {

		assertThrows(IllegalArgumentException.class, () -> service.resolveRange(null, RangeRequest.full()));
	}

	@Test
	void shouldRejectNonChunkedFile() {

		MasterFile legacyFile = new MasterFile();
		legacyFile.setSize(50L);

		assertThrows(IllegalArgumentException.class, () -> service.resolveRange(legacyFile, RangeRequest.full()));
	}

	@Test
	void shouldMapRangeInsideSingleChunk() {

		mockUploadJob();

		mockChunk(0, 20L);

		ChunkRangeService.ResolvedChunkRange result = service.resolve(masterFile, RangeRequest.startEnd(5, 15));

		assertEquals(1, result.getChunks().size());

		ChunkRangeService.ChunkRange chunk = result.getChunks().get(0);

		assertEquals(0, chunk.getChunkIndex());

		assertEquals(5, chunk.getLocalStart());

		assertEquals(15, chunk.getLocalEnd());

		assertEquals(11, chunk.getLength());
	}

	@Test
	void shouldMapCrossChunkRange() {

		mockUploadJob();

		mockChunk(0, 20L);
		mockChunk(1, 20L);
		mockChunk(2, 10L);

		ChunkRangeService.ResolvedChunkRange result = service.resolve(masterFile, RangeRequest.startEnd(15, 45));

		assertEquals(3, result.getChunks().size());

		ChunkRangeService.ChunkRange first = result.getChunks().get(0);

		assertEquals(0, first.getChunkIndex());

		assertEquals(15, first.getLocalStart());

		assertEquals(19, first.getLocalEnd());

		assertEquals(5, first.getLength());

		ChunkRangeService.ChunkRange second = result.getChunks().get(1);

		assertEquals(1, second.getChunkIndex());

		assertEquals(0, second.getLocalStart());

		assertEquals(19, second.getLocalEnd());

		assertEquals(20, second.getLength());

		ChunkRangeService.ChunkRange third = result.getChunks().get(2);

		assertEquals(2, third.getChunkIndex());

		assertEquals(0, third.getLocalStart());

		assertEquals(5, third.getLocalEnd());

		assertEquals(6, third.getLength());
	}

	@Test
	void shouldMapSuffixRange() {

		mockUploadJob();

		mockChunk(2, 10L);

		ChunkRangeService.ResolvedChunkRange result = service.resolve(masterFile, RangeRequest.suffix(10));

		assertEquals(1, result.getChunks().size());

		ChunkRangeService.ChunkRange chunk = result.getChunks().get(0);

		assertEquals(2, chunk.getChunkIndex());

		assertEquals(0, chunk.getLocalStart());

		assertEquals(9, chunk.getLocalEnd());

		assertEquals(10, chunk.getLength());
	}

	@Test
	void shouldClampEndBeyondFileSize() {

		ByteRange range = service.resolveRange(masterFile, RangeRequest.startEnd(40, 100));

		assertEquals(40L, range.getStart());

		assertEquals(49L, range.getEnd());

		assertEquals(10L, range.getLength());
	}

	@Test
	void shouldRejectInvalidStartEndRange() {

		assertThrows(IllegalArgumentException.class,
				() -> service.resolveRange(masterFile, RangeRequest.startEnd(20, 10)));
	}

	private void mockUploadJob() {

		UploadJob uploadJob = new UploadJob();

		uploadJob.setId("upload-1");
		uploadJob.setTotalSize(50L);
		uploadJob.setTotalChunks(3);
		uploadJob.setChunkSize(20);

		when(uploadJobRepository.findById("upload-1")).thenReturn(Optional.of(uploadJob));
	}

	private void mockChunk(int index, long size) {

		UploadChunk chunk = new UploadChunk();

		chunk.setUploadJobId("upload-1");
		chunk.setChunkIndex(index);
		chunk.setTelegramFileId("telegram-file-" + index);
		chunk.setSize(size);

		when(uploadChunkRepository.findByUploadJobIdAndChunkIndex("upload-1", index)).thenReturn(Optional.of(chunk));
	}
}