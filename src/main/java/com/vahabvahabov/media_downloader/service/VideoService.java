package com.vahabvahabov.media_downloader.service;

import com.vahabvahabov.media_downloader.model.VideoRequest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface VideoService {
    CompletableFuture<String> downloadVideoAsync(VideoRequest request, String sessionId);
    Resource getDownloadedVideo(String fileName) throws IOException;
    void deleteDownloadedVideo(String fileName);
    String getDisplayName(String fileName);
    void cancelDownload(String sessionId);
    public Map<String, Object> getMediaInfo(String url, String platform);
}