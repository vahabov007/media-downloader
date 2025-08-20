package com.vahabvahabov.media_downloader.controller.mc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class DownloadProgressController {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public DownloadProgressController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendMessage(String sessionId, String message) {
        messagingTemplate.convertAndSendToUser(sessionId, "/topic/progress", message);
    }
}