package com.vahabvahabov.media_downloader.controller.mc;

import com.vahabvahabov.media_downloader.model.VideoRequest;
import com.vahabvahabov.media_downloader.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/videos")
public class VideoController {
    private final VideoService videoService;
    private final Logger logger = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/download")
    public ResponseEntity<String> startDownload(
            @RequestBody VideoRequest videoRequest,
            @RequestHeader("X-Session-ID") String sessionId) {

        logger.info("Received download request for URL: {}, Platform: {}, Quality: {}, Session: {}",
                videoRequest.getUrl(), videoRequest.getPlatform(), videoRequest.getQuality(), sessionId);

        try {
            CompletableFuture<String> future = videoService.downloadVideoAsync(videoRequest, sessionId)
                    .whenComplete((filename, ex) -> {
                        if (ex != null) {
                            logger.error("Download failed for session: {} with error: {}", sessionId, ex.getMessage(), ex);
                        } else {
                            logger.info("Download completed for session: {} with filename: {}", sessionId, filename);
                        }
                    });

            return ResponseEntity.ok("Download started. Check progress via WebSocket.");
        } catch (Exception e) {
            logger.error("Failed to start download for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to start download: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Resource resource = videoService.getDownloadedVideo(fileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + videoService.getDisplayName(fileName) + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/cleanup/{fileName}")
    public ResponseEntity<Void> cleanupFile(@PathVariable String fileName) {
        videoService.deleteDownloadedVideo(fileName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelDownload(@RequestHeader("X-Session-ID") String sessionId) {
        videoService.cancelDownload(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@RequestBody VideoRequest videoRequest) {
        Map<String, Object> info = videoService.getMediaInfo(videoRequest.getUrl(), videoRequest.getPlatform());
        return ResponseEntity.ok(info);
    }
}