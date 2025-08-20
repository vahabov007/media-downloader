document.addEventListener('DOMContentLoaded', () => {
    const platformButtons = document.querySelectorAll('.platform-selector .platform-btn');
    const videoUrlInput = document.getElementById('url-input');
    const qualitySelect = document.getElementById('quality-select');
    const qualityGroup = document.getElementById('quality-group');
    const cookiesGroup = document.getElementById('cookies-group'); // YENİ: cookies grubu
    const cookiesInput = document.getElementById('cookies-input'); // YENİ: cookies inputu
    const downloadBtn = document.getElementById('download-btn');
    const statusMessage = document.getElementById('status-message');
    const progressWrapper = document.getElementById('progress-wrapper');
    const progressBar = document.getElementById('progress-bar');
    const deleteBtn = document.getElementById('delete-btn');
    const container = document.querySelector('.container');
    const urlLabel = document.querySelector('label[for="url-input"]');
    const infoBtn = document.getElementById('info-btn');
    const infoModal = document.getElementById('info-modal');
    const closeInfoModal = document.querySelector('.close-info-modal');

    const successModal = document.getElementById('download-success-modal');
    const closeSuccessModal = document.querySelector('.close-success-modal');
    const downloadedMediaThumbnail = document.getElementById('downloaded-media-thumbnail');
    const downloadedMediaTitle = document.getElementById('downloaded-media-title');
    const comingSoonModal = document.getElementById('coming-soon-modal');
    const closeComingSoon = document.querySelector('.close-coming-soon');
    const errorModal = document.getElementById('error-modal');
    const closeErrorModal = document.querySelector('.close-error-modal');
    const errorMessageText = document.getElementById('error-message-text');

    let currentPlatform = 'youtube';
    let stompClient = null;
    let sessionId = localStorage.getItem('sessionId');
    let subscription = null;
    let isDownloading = false;

    if (!sessionId) {
        sessionId = uuidv4();
        localStorage.setItem('sessionId', sessionId);
    }

    updateUI(currentPlatform);

    platformButtons.forEach(button => {
        button.addEventListener('click', () => {
            if (isDownloading) return;
            platformButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');
            currentPlatform = button.dataset.platform;
            videoUrlInput.placeholder = `Enter ${currentPlatform} URL...`;
            urlLabel.textContent = `Enter ${currentPlatform} URL:`;
            updateUI(currentPlatform);

            if (currentPlatform === 'spotify') {
                showComingSoonModal();
                downloadBtn.disabled = true;
            } else {
                downloadBtn.disabled = false;
                if (comingSoonModal.style.display === 'flex') {
                    comingSoonModal.style.display = 'none';
                }
            }
        });
    });

    downloadBtn.addEventListener('click', async () => {
        const url = videoUrlInput.value.trim();
        const quality = (currentPlatform === 'youtube') ? qualitySelect.value : 'best';
        const cookies = cookiesInput.value.trim(); // YENİ: Kukiləri daxil et

        if (!url || url.length > videoUrlInput.maxLength) {
            showErrorModal('Please enter a valid URL (max 200 characters).');
            return;
        }

        if (!isUrlValid(url, currentPlatform)) {
            showErrorModal(`Please enter a valid ${currentPlatform} URL.`);
            return;
        }

        if (isDownloading) {
            showStatus('A download is already in progress.', 'info');
            return;
        }

        isDownloading = true;
        downloadBtn.disabled = true;
        infoBtn.disabled = true;
        showStatus('Connecting to server...', 'info');
        resetProgress();

        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);

        stompClient.connect({'X-Session-ID': sessionId}, (frame) => {
            showStatus('Connected. Processing...', 'info');
            deleteBtn.classList.remove('hidden');
            progressWrapper.classList.remove('hidden');

            subscription = stompClient.subscribe(`/user/topic/progress`, (message) => {
                const body = message.body;

                if (body.startsWith('Progress:')) {
                    const progressLine = body.substring(10).trim();
                    if (progressLine.startsWith('[download]')) {
                        const percentMatch = progressLine.match(/([0-9]+\.?[0-9]*)%/);
                        if (percentMatch) {
                            const percent = parseFloat(percentMatch[1]);
                            updateProgress(percent);
                            showStatus(`Downloading: ${percent}%`, 'info');
                            return;
                        }
                    }
                    showStatus(`Processing: ${progressLine}`, 'info');
                } else if (body.startsWith('Download finished:')) {
                    const fileName = body.substring(18).trim();
                    statusMessage.style.display = 'none';

                    // Info API çağırışı
                    fetch(`/api/videos/info`, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({
                            url: url,
                            platform: currentPlatform,
                            cookies: cookies
                        })
                    })
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Video info not found');
                            }
                            return response.json();
                        })
                        .then(data => {
                            showSuccessModal(data.title, data.thumbnail); // Thumbnail və Title üçün məlumat
                        })
                        .catch(error => {
                            console.error('Failed to get video info:', error);
                            showSuccessModal("Downloaded", null); // Məlumat tapılmasa default göstərin
                        })
                        .finally(() => {
                            isDownloading = false;
                            downloadBtn.disabled = false;
                            infoBtn.disabled = false;
                            resetProgress();
                            disconnectStomp();
                        });

                    fetch(`/api/videos/download/${encodeURIComponent(fileName)}`)
                        .then(response => {
                            if (response.ok) return response.blob();
                            throw new Error('Download failed');
                        })
                        .then(blob => {
                            const url = window.URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = fileName;
                            document.body.appendChild(a);
                            a.click();
                            window.URL.revokeObjectURL(url);
                            document.body.removeChild(a);

                            fetch(`/api/videos/cleanup/${encodeURIComponent(fileName)}`, {
                                method: 'DELETE'
                            });
                        })
                        .catch(error => {
                            showErrorModal(`Error triggering download: ${error.message}`);
                        });

                } else if (body.startsWith('Error:')) {
                    const errorMsg = body.substring(6).trim();
                    showErrorModal(errorMsg);
                    isDownloading = false;
                    downloadBtn.disabled = false;
                    infoBtn.disabled = false;
                    resetProgress();
                    disconnectStomp();
                } else if (body === 'Download canceled') {
                    showStatus('Download canceled successfully.', 'info');
                    isDownloading = false;
                    downloadBtn.disabled = false;
                    infoBtn.disabled = false;
                    resetProgress();
                    disconnectStomp();
                }
            });

            fetch('/api/videos/download', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Session-ID': sessionId
                },
                body: JSON.stringify({
                    url: url,
                    quality: quality,
                    platform: currentPlatform,
                    cookies: cookies // YENİ: Kukiləri back-endə göndər
                })
            }).catch(error => {
                showErrorModal('Failed to start download: ' + error.message);
                isDownloading = false;
                downloadBtn.disabled = false;
                infoBtn.disabled = false;
                resetProgress();
            });
        }, (error) => {
            showErrorModal('Connection error: ' + error);
            isDownloading = false;
            downloadBtn.disabled = false;
            infoBtn.disabled = false;
            resetProgress();
        });
    });

    infoBtn.addEventListener('click', () => {
        infoModal.style.display = 'flex';
    });

    closeInfoModal.addEventListener('click', () => {
        infoModal.style.display = 'none';
    });

    window.addEventListener('click', (event) => {
        if (event.target === infoModal) {
            infoModal.style.display = 'none';
        }
    });

    deleteBtn.addEventListener('click', () => {
        if (isDownloading) {
            showStatus('Download canceled successfully.', 'info');
            isDownloading = false;
            downloadBtn.disabled = false;
            infoBtn.disabled = false;
            resetProgress();

            fetch('/api/videos/cancel', {
                method: 'POST',
                headers: {
                    'X-Session-ID': sessionId
                }
            }).catch(error => {
                console.error('Failed to cancel download on server:', error);
            }).finally(() => {
                disconnectStomp();
            });
        }
    });

    function showErrorModal(message) {
        errorMessageText.textContent = message;
        errorModal.style.display = 'flex';
        closeErrorModal.onclick = () => {
            errorModal.style.display = 'none';
        };
        window.onclick = (event) => {
            if (event.target === errorModal) {
                errorModal.style.display = 'none';
            }
        };
    }

    function showSuccessModal(title, thumbnail) {
        if (thumbnail) {
            downloadedMediaThumbnail.src = thumbnail;
            downloadedMediaThumbnail.style.display = 'block';
        } else {
            downloadedMediaThumbnail.style.display = 'none';
        }
        downloadedMediaTitle.textContent = title;
        successModal.style.display = 'flex';
        closeSuccessModal.onclick = () => {
            successModal.style.display = 'none';
        };
        window.onclick = (event) => {
            if (event.target === successModal) {
                successModal.style.display = 'none';
            }
        };
    }

    function showComingSoonModal() {
        comingSoonModal.style.display = 'flex';
        closeComingSoon.onclick = () => {
            comingSoonModal.style.display = 'none';
        };
        window.onclick = (event) => {
            if (event.target === comingSoonModal) {
                comingSoonModal.style.display = 'none';
            }
        };
    }

    function showStatus(message, type) {
        statusMessage.textContent = message;
        statusMessage.className = `status-message show ${type}`;
    }

    function updateProgress(percent) {
        progressWrapper.classList.remove('hidden');
        progressBar.style.width = `${percent}%`;
    }

    function resetProgress() {
        progressWrapper.classList.add('hidden');
        progressBar.style.width = '0%';
        deleteBtn.classList.add('hidden');
        statusMessage.style.display = 'none';
    }

    function updateUI(platform) {
        let color = '';
        if (platform === 'youtube') {
            color = '#ff0000';
            qualityGroup.style.display = 'block';
            cookiesGroup.style.display = 'block'; // YENİ: YouTube üçün cookies göstər
        } else if (platform === 'instagram') {
            color = '#e6683c';
            qualityGroup.style.display = 'none';
            cookiesGroup.style.display = 'none';
        } else if (platform === 'spotify') {
            color = '#1DB954';
            qualityGroup.style.display = 'none';
            cookiesGroup.style.display = 'none';
        } else {
            color = '#fff';
            qualityGroup.style.display = 'block';
            cookiesGroup.style.display = 'block';
        }
        container.style.boxShadow = `0 10px 30px ${color}40`;
        container.style.borderColor = `${color}20`;
        qualitySelect.style.setProperty('--focus-color', color);
    }

    function disconnectStomp() {
        if (subscription) {
            subscription.unsubscribe();
            subscription = null;
        }
        if (stompClient) {
            stompClient.disconnect(() => {
                console.log("STOMP connection disconnected.");
            });
            stompClient = null;
        }
    }

    function isUrlValid(url, platform) {
        if (!url) {
            return false;
        }

        const patterns = {
            youtube: /^(?:https?:\/\/)?(?:www\.)?(?:youtube\.com|youtu\.be)\/.*(?:v=|embed\/|shorts\/|e\/|v=|\/c\/|\/user\/|watch\?v=|\/)/,
            instagram: /(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:p|reel|tv|stories|[\w.-]+)\/([\w-]+)\/?/,
            spotify: /^(?:https?:\/\/)?(?:open\.spotify\.com)\/(?:track|album|playlist|artist)\/[a-zA-Z0-9_-]+(?:\/|\?.*)?$/
        };

        if (platform in patterns) {
            return patterns[platform].test(url);
        }

        return false;
    }

    function uuidv4() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }
});