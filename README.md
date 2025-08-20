🌟 Media Downloader
Effortlessly download videos and audio from your favorite platforms with this powerful and user-friendly web application.

Built on the robust Spring Boot framework and powered by the versatile yt-dlp and FFmpeg command-line tools, this project provides a seamless solution for media retrieval. Whether it's a video from YouTube or a post from Instagram, our application makes it easy to get the content you want, in the quality you need.

🚀 Key Features
Multi-Platform Support: Download media from popular sites like YouTube, Instagram, and more.

Flexible Quality Options: Choose from a range of video qualities (1080p, 720p, 480p, etc.) to suit your needs.

Real-Time Progress Tracking: Monitor your download's status with live updates via WebSocket technology.

Asynchronous Processing: Powered by CompletableFuture, the application handles downloads asynchronously, ensuring a smooth and responsive user experience without blocking the main thread.

Robust & Secure: The application handles common download errors gracefully and includes built-in cleanup mechanisms for temporary files.

Dockerized Deployment: Easily run the application in any environment with the provided Dockerfile and docker-compose.yml file.

🛠️ Technologies Used
Backend: Spring Boot, Java

Core Tools: yt-dlp (for parsing and downloading) & FFmpeg (for media conversion and merging)

Frontend: HTML, CSS, JavaScript (with SockJS and Stomp.js for WebSockets)

Containerization: Docker, Docker Compose

💡 How It Works
Request: The user submits a URL through the simple web interface.

Processing: The backend service uses a combination of yt-dlp to fetch the media streams and FFmpeg to merge them into a single, high-quality video or audio file.

Real-time Updates: Progress messages are streamed back to the user's browser via a WebSocket connection.

Download: Once the process is complete, the user receives a direct link to download the final file, which is then automatically cleaned up from the server.

This project is perfect for developers interested in learning about asynchronous programming with Spring Boot, integrating external command-line tools, and implementing real-time communication with WebSockets.

