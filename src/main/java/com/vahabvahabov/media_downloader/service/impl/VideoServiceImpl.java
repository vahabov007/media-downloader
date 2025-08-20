package com.vahabvahabov.media_downloader.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vahabvahabov.media_downloader.controller.mc.DownloadProgressController;
import com.vahabvahabov.media_downloader.model.VideoRequest;
import com.vahabvahabov.media_downloader.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class VideoServiceImpl implements VideoService {
    private static final Logger logger = LoggerFactory.getLogger(VideoServiceImpl.class);

    @Value("${download.dir:downloaded_videos}")
    private String downloadDir;

    @Value("${yt.dlp.path}")
    private String ytDlpPath;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Autowired
    private DownloadProgressController progressController;

    private final ConcurrentHashMap<String, String> fileDisplayNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> activeSessions = new ConcurrentHashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public CompletableFuture<String> downloadVideoAsync(VideoRequest request, String sessionId) {
        logger.info("Entering downloadVideoAsync for session: {}", sessionId);
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting async download for session: {}, URL: {}", sessionId, request.getUrl());
            if (activeSessions.putIfAbsent(sessionId, true) != null) {
                logger.warn("Download already in progress for session: {}", sessionId);
                progressController.sendMessage(sessionId, "Error: A download is already in progress.");
                throw new IllegalStateException("A download is already in progress for this session.");
            }

            Path tempCookiesFile = null;

            try {
                logger.info("Validating request for session: {}", sessionId);
                if (request.getUrl().length() > 200) {
                    throw new IllegalArgumentException("URL is too long. Max length is 200 characters.");
                }

                String platform = request.getPlatform();
                String url = request.getUrl();
                String cookies = request.getCookies(); // Yeni dəyişiklik: Cookies götürülür

                // Cookies verilərsə, müvəqqəti fayl yaradın
                if (cookies != null && !cookies.isEmpty()) {
                    try {
                        tempCookiesFile = Files.createTempFile("yt-dlp-cookies", ".txt");
                        Files.writeString(tempCookiesFile, cookies);
                        logger.info("Temporary cookies file created at: {}", tempCookiesFile);
                    } catch (IOException e) {
                        logger.error("Failed to create temporary cookies file", e);
                        tempCookiesFile = null;
                    }
                }

                logger.info("Fetching video name for session: {}, URL: {}", sessionId, url);
                String title = getVideoName(url, platform, tempCookiesFile); // Yeni dəyişiklik: cookies faylı ötürülür

                File dir = new File(downloadDir);
                if (!dir.exists()) dir.mkdirs();
                logger.info("Download directory prepared: {}", downloadDir);

                String format;
                if ("instagram".equals(platform)) {
                    format = "best";
                } else {
                    switch (request.getQuality() == null ? "best" : request.getQuality()) {
                        case "1080p":
                            format = "bestvideo[height<=1080]+bestaudio/best";
                            break;
                        case "720p":
                            format = "bestvideo[height<=720]+bestaudio/best";
                            break;
                        case "480p":
                            format = "bestvideo[height<=480]+bestaudio/best";
                            break;
                        case "360p":
                            format = "bestvideo[height<=360]+bestaudio/best";
                            break;
                        case "144p":
                            format = "bestvideo[height<=144]+bestaudio/best";
                            break;
                        default:
                            format = "best";
                            break;
                    }
                }

                String finalFileName = sessionId + ".mp4";
                String finalFilePath = downloadDir + "/" + finalFileName;

                logger.info("Processing URL: {} for session: {}", url, sessionId);
                if (!activeSessions.containsKey(sessionId)) {
                    logger.warn("Download canceled for session: {} during processing", sessionId);
                    throw new InterruptedException("Download canceled");
                }

                int exitCode = executeDownload(format, finalFilePath, url, sessionId, tempCookiesFile); // Yeni dəyişiklik: cookies faylı ötürülür

                if (exitCode != 0) {
                    throw new RuntimeException("Download failed with exit code: " + exitCode);
                }

                activeSessions.remove(sessionId);
                activeProcesses.remove(sessionId);
                logger.info("Download completed. Session and process cleaned up for session: {}", sessionId);

                Path downloadedFilePath = Paths.get(finalFilePath);
                if (!Files.exists(downloadedFilePath)) {
                    throw new RuntimeException("No downloaded file found. Please try again.");
                }

                String displayName = sanitizeFilename(title) + ".mp4";
                fileDisplayNames.put(finalFileName, displayName);
                progressController.sendMessage(sessionId, "Download finished: " + finalFileName);
                logger.info("Returning filename: {} for session: {}", finalFileName, sessionId);
                return finalFileName;

            } catch (Exception e) {
                activeSessions.remove(sessionId);
                activeProcesses.remove(sessionId);
                logger.error("Download failed for session: {} with error: {}", sessionId, e.getMessage(), e);
                progressController.sendMessage(sessionId, "Error: " + e.getMessage());
                deletePartialFiles(sessionId);
                throw new CompletionException(e);
            } finally {
                // Müvəqqəti faylı silmək
                if (tempCookiesFile != null) {
                    try {
                        Files.deleteIfExists(tempCookiesFile);
                        logger.info("Temporary cookies file deleted: {}", tempCookiesFile);
                    } catch (IOException e) {
                        logger.error("Failed to delete temporary cookies file: {}", tempCookiesFile, e);
                    }
                }
            }
        }, executorService);
    }

    private int executeDownload(String format, String finalFilePath, String url, String sessionId, Path cookiesFile) { // Yeni dəyişiklik: cookiesFile qəbul edir
        try {
            List<String> command = new ArrayList<>();
            command.add(ytDlpPath);
            command.add("--no-cache-dir");
            command.add("--user-agent");
            command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            command.add("--force-ipv4");
            command.add("--retries");
            command.add("10");
            command.add("--socket-timeout");
            command.add("30");
            command.add("--buffer-size");
            command.add("16K");

            command.add("--concurrent-fragments");
            command.add("5");
            command.add("--limit-rate");
            command.add("10M");
            command.add("--http-chunk-size");
            command.add("10M");

            command.add("-f");
            command.add(format);
            command.add("--ffmpeg-location");
            command.add(ffmpegPath);
            command.add("--merge-output-format");
            command.add("mp4");
            command.add("--postprocessor-args");
            command.add("Merger:-strict -2");
            command.add("-o");
            command.add(finalFilePath);

            // Yeni dəyişiklik: Cookies faylı əlavə edilir
            if (cookiesFile != null) {
                command.add("--cookies");
                command.add(cookiesFile.toString());
            }

            if (url.contains("youtube.com/shorts") || url.contains("youtube.com/playlist")) {
                command.add("--no-playlist");
            }
            command.add(url);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeProcesses.put(sessionId, process);
            logger.info("Started download process for session: {} (PID: {}, URL: {})",
                    sessionId, process.pid(), url);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && activeSessions.containsKey(sessionId)) {
                    if (line.startsWith("[download]") || line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]")) {
                        progressController.sendMessage(sessionId, "Progress: " + line);
                    }
                    logger.debug("Process output: {}", line);
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 minutes timeout
            if (finished) {
                return process.exitValue();
            } else {
                process.destroyForcibly();
                return -1;
            }

        } catch (Exception e) {
            logger.error("Error during download for session: {}", sessionId, e);
            return -1;
        }
    }

    private String getVideoName(String url, String platform, Path cookiesFile) throws Exception { // Yeni dəyişiklik: cookiesFile qəbul edir
        String printField = "instagram".equals(platform) ? "description" : "title";

        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);
        command.add("--no-cache-dir");
        command.add("--user-agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        command.add("--force-ipv4");
        command.add("--retries");
        command.add("5");
        command.add("--socket-timeout");
        command.add("15");
        command.add("--print");
        command.add(printField);
        command.add("--skip-download");

        // Yeni dəyişiklik: Cookies faylı əlavə edilir
        if (cookiesFile != null) {
            command.add("--cookies");
            command.add(cookiesFile.toString());
        }

        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout while fetching video information");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String rawError = errorOutput.toString().trim();
            String friendlyError = getFriendlyErrorMessage(rawError, platform);
            throw new RuntimeException(friendlyError);
        }

        String name = output.toString().trim();
        if (name.isEmpty()) {
            throw new RuntimeException("Failed to retrieve video title");
        }

        if ("instagram".equals(platform)) {
            String[] sentences = name.split("\\. ");
            name = sentences[0];
            if (name.length() > 100) {
                name = name.substring(0, 97) + "...";
            }
        }

        return name.trim();
    }

    private void zipDirectory(String sourceDirPath, String zipPath) throws IOException {
        Path zipFile = Paths.get(zipPath);
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Path sourceDir = Paths.get(sourceDirPath);
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            logger.error("Error zipping file: {}", path, e);
                        }
                    });
        }
    }

    @Override
    public void cancelDownload(String sessionId) {
        activeSessions.remove(sessionId);
        Process process = activeProcesses.remove(sessionId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            logger.info("Download canceled for session: {} (Process PID: {})", sessionId, process.pid());
        } else {
            logger.info("Download canceled for session: {} (before process started or already completed)", sessionId);
        }
        progressController.sendMessage(sessionId, "Download canceled successfully");
        deletePartialFiles(sessionId);
    }

    private void deletePartialFiles(String sessionId) {
        try {
            Path dirPath = Paths.get(downloadDir);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith(sessionId + ".") || name.equals(sessionId);
                        })
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.info("Deleted partial file: {}", path);
                            } catch (IOException e) {
                                logger.error("Failed to delete partial file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.error("Error cleaning up partial files for session: {}", sessionId, e);
        }
    }

    private String getFriendlyErrorMessage(String rawError, String platform) {
        String lowerError = rawError.toLowerCase();
        if (lowerError.contains("instagram") && (lowerError.contains("cookies") || lowerError.contains("login") || lowerError.contains("granting access"))) {
            return "This Instagram post appears to be private or restricted. We currently support only public posts. For private content, use yt-dlp directly with your browser cookies.";
        } else if (lowerError.contains("video unavailable") || lowerError.contains("does not exist") || lowerError.contains("no video formats")) {
            return "The media is unavailable or the URL is invalid. Please check and try again.";
        } else if (lowerError.contains("geo-restricted") || lowerError.contains("not available in your country")) {
            return "This content is geo-restricted and not available in the server's location.";
        } else if (lowerError.contains("age-restricted")) {
            return "This content is age-restricted. Consider using yt-dlp with cookies from a verified account.";
        } else if (lowerError.contains("unsupported url")) {
            return "Unsupported URL format. Ensure it's a valid " + platform + " link.";
        } else if (rawError.isEmpty()) {
            return "An unknown error occurred during download. Please try again later.";
        } else {
            String[] lines = rawError.split("\n");
            for (String l : lines) {
                if (l.startsWith("ERROR:")) {
                    return l.substring(6).trim() + " Please check the URL or try updating yt-dlp.";
                }
            }
            return "An error occurred: " + rawError.substring(0, Math.min(200, rawError.length())) + "... Please try again.";
        }
    }

    private String sanitizeFilename(String title) {
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        return sanitized;
    }

    @Override
    public Resource getDownloadedVideo(String fileName) throws IOException {
        Path filePath = Paths.get(downloadDir, fileName);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + fileName);
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public void deleteDownloadedVideo(String fileName) {
        try {
            Path filePath = Paths.get(downloadDir, fileName);
            Files.deleteIfExists(filePath);
            if (fileName.endsWith(".zip")) {
                String sessionId = fileName.substring(0, fileName.length() - 4);
                Path dirPath = Paths.get(downloadDir, sessionId);
                if (Files.exists(dirPath)) {
                    Files.walk(dirPath)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    logger.error("Failed to delete: {}", path, e);
                                }
                            });
                }
            }
            fileDisplayNames.remove(fileName);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", fileName, e);
        }
    }

    @Override
    public String getDisplayName(String fileName) {
        return fileDisplayNames.getOrDefault(fileName, fileName);
    }

    @Override
    public Map<String, Object> getMediaInfo(String url, String platform) {
        Path tempCookiesFile = null;
        try {
            // Placeholder for cookies logic if needed in getMediaInfo
            // Since you haven't provided a way to send cookies for this method,
            // we will not implement it here to avoid errors.
            // If the user can send cookies, you would add the same logic as in downloadVideoAsync.

            StringBuilder jsonOutput = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            List<String> command = new ArrayList<>();
            command.add(ytDlpPath);
            command.add("--no-cache-dir");
            command.add("--user-agent");
            command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            command.add("--force-ipv4");
            command.add("--retries");
            command.add("5");
            command.add("--socket-timeout");
            command.add("15");
            command.add("-J");
            command.add("--skip-download");
            command.add(url);

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // Read stdout
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonOutput.append(line).append("\n");
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Timeout while fetching media information");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String rawError = errorOutput.toString().trim();
                String friendlyError = getFriendlyErrorMessage(rawError, platform);
                throw new RuntimeException(friendlyError);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> info = objectMapper.readValue(jsonOutput.toString(), Map.class);
            Map<String, Object> selectedInfo = new HashMap<>();
            selectedInfo.put("title", info.get("title"));
            selectedInfo.put("uploader", info.get("uploader"));
            selectedInfo.put("duration", info.get("duration"));
            selectedInfo.put("view_count", info.get("view_count"));
            selectedInfo.put("upload_date", info.get("upload_date"));
            selectedInfo.put("thumbnail", info.get("thumbnail"));
            return selectedInfo;

        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve media info: " + e.getMessage());
        } finally {
            if (tempCookiesFile != null) {
                try {
                    Files.deleteIfExists(tempCookiesFile);
                    logger.info("Temporary cookies file deleted: {}", tempCookiesFile);
                } catch (IOException e) {
                    logger.error("Failed to delete temporary cookies file: {}", tempCookiesFile, e);
                }
            }
        }
    }
}