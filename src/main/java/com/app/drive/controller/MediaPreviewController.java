package com.app.drive.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;

/**
 * Creates a temporary browser-compatible H.264/AAC preview from a stored video.
 * The original file remains unchanged in the configured storage provider.
 */
@RestController
@RequestMapping("/stream")
public class MediaPreviewController {

    private static final Logger logger = LoggerFactory.getLogger(MediaPreviewController.class);
    private static final long PROCESS_TIMEOUT_MINUTES = 30;

    private final MasterFileRepository repository;
    private final FileTransferService fileTransferService;
    private final String ffmpegPath;
    private final boolean enabled;
    private final long maxInputSizeBytes;

    public MediaPreviewController(
            MasterFileRepository repository,
            FileTransferService fileTransferService,
            @Value("${app.media.preview.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${app.media.preview.enabled:true}") boolean enabled,
            @Value("${app.media.preview.max-input-size-bytes:1073741824}") long maxInputSizeBytes) {
        this.repository = repository;
        this.fileTransferService = fileTransferService;
        this.ffmpegPath = ffmpegPath;
        this.enabled = enabled;
        this.maxInputSizeBytes = maxInputSizeBytes;
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<StreamingResponseBody> preview(
            @PathVariable String id, Authentication auth) {

        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Compatible video previews are disabled by server configuration.");
        }

        MasterFile file = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName())
                .orElseThrow(com.app.core.exception.FileNotFoundException::new);

        if (!"FILE".equalsIgnoreCase(file.getDriveType())
                || file.getContentType() == null
                || !file.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Compatible preview is available for video files only.");
        }

        if (file.getSize() == null || file.getSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The video has no valid file size.");
        }
        if (file.getSize() > maxInputSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "This video exceeds the configured compatible-preview size limit.");
        }
        if (!fileTransferService.isChunked(file) && !fileTransferService.isLegacy(file)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The video has no valid storage reference.");
        }

        Path workDirectory = null;
        try {
            workDirectory = Files.createTempDirectory("fileserver-video-preview-");
            Path input = workDirectory.resolve("source.bin");
            Path output = workDirectory.resolve("preview.mp4");
            Path errorLog = workDirectory.resolve("ffmpeg-error.log");

            // Temporary processing only: no converted copy is written to Telegram or the database.
            try (var inputStream = Files.newOutputStream(input)) {
                fileTransferService.streamRange(file, new ByteRange(0L, file.getSize() - 1L), inputStream);
            }

            Process process;
            try {
                process = new ProcessBuilder(
                        ffmpegPath,
                        "-hide_banner", "-loglevel", "error", "-y",
                        "-i", input.toAbsolutePath().toString(),
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-crf", "23",
                        "-pix_fmt", "yuv420p",
                        "-c:a", "aac",
                        "-b:a", "128k",
                        "-movflags", "+faststart",
                        "-sn", "-dn",
                        "-f", "mp4",
                        output.toAbsolutePath().toString())
                        .redirectError(errorLog.toFile())
                        .start();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "FFmpeg is not available. Install FFmpeg or configure app.media.preview.ffmpeg-path.", ex);
            }

            boolean finished;
            try {
                finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Video preview conversion was interrupted.", ex);
            }

            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "Video preview conversion exceeded the server time limit.");
            }

            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                String detail = readErrorLog(errorLog);
                logger.warn("FFmpeg preview conversion failed for file {}: {}", id, detail);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "This video could not be converted for browser playback.");
            }

            final Path previewFile = output;
            final Path cleanupDirectory = workDirectory;
            long outputSize = Files.size(previewFile);
            String previewName = safePreviewName(file.getName());

            StreamingResponseBody body = responseStream -> {
                try {
                    Files.copy(previewFile, responseStream);
                    responseStream.flush();
                } finally {
                    deleteTemporaryDirectory(cleanupDirectory);
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .contentLength(outputSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "none")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(previewName).build().toString())
                    .body(body);

        } catch (ResponseStatusException ex) {
            deleteTemporaryDirectory(workDirectory);
            throw ex;
        } catch (IOException ex) {
            deleteTemporaryDirectory(workDirectory);
            logger.error("Unable to create compatible preview for file {}", id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to prepare the compatible video preview.", ex);
        }
    }

    private String readErrorLog(Path errorLog) {
        try {
            if (!Files.exists(errorLog)) {
                return "No FFmpeg diagnostic output was produced.";
            }
            String text = Files.readString(errorLog);
            return text.length() > 3000 ? text.substring(text.length() - 3000) : text;
        } catch (IOException ex) {
            return "Unable to read FFmpeg diagnostic output.";
        }
    }

    private String safePreviewName(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "video" : originalName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[\\\\/\\r\\n\\\"]", "_");
        return name + "-preview.mp4";
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    logger.warn("Unable to delete temporary preview file {}", path, ex);
                }
            });
        } catch (IOException ex) {
            logger.warn("Unable to clean temporary preview directory {}", directory, ex);
        }
    }
}
