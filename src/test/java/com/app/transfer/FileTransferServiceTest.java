package com.app.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.app.master.entity.MasterFile;
import com.app.storage.factory.StorageFactory;
import com.app.storage.service.StorageService;
import com.app.upload.service.UploadChunkReader;

public class FileTransferServiceTest {

	@Mock
	private ChunkRangeService chunkRangeService;

	@Mock
	private UploadChunkReader uploadChunkReader;

	@Mock
	private StorageFactory storageFactory;

	@Mock
	private StorageService storageService;

	private FileTransferService service;

	@BeforeEach
	void setUp() {

		MockitoAnnotations.openMocks(this);

		service = new FileTransferService(chunkRangeService, uploadChunkReader, storageFactory);
	}

	@Test
	void shouldStreamLegacyRange() throws Exception {

		MasterFile file = new MasterFile();

		file.setId("file1");
		file.setFileId("telegram-file");
		file.setSize(10L);

		byte[] content = "0123456789".getBytes();

		when(storageFactory.get()).thenReturn(storageService);
		org.mockito.Mockito.when(storageService.stream(
				org.mockito.ArgumentMatchers.eq("telegram-file"),
				org.mockito.ArgumentMatchers.eq(2L),
				org.mockito.ArgumentMatchers.eq(5L),
				org.mockito.ArgumentMatchers.any(OutputStream.class)))
			.thenAnswer(invocation -> {
				OutputStream output = invocation.getArgument(3);
				output.write(content, 2, 5);
				return 5L;
			});

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.streamRange(file, new ByteRange(2L, 6L), output);

		assertArrayEquals("23456".getBytes(), output.toByteArray());
	}
}