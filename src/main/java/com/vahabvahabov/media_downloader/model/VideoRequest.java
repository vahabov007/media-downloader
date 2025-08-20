package com.vahabvahabov.media_downloader.model;

import lombok.Data;

@Data
public class VideoRequest {
    private String url;
    private String quality;
    private String platform;
}