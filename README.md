# 🌟 Media Downloader

Effortlessly download videos and audio from your favorite platforms with this powerful and user-friendly web application.

Built on the robust **Spring Boot** framework and powered by the versatile **yt-dlp** and **FFmpeg** command-line tools, this project provides a seamless solution for media retrieval.  
Whether it's a video from YouTube or a post from Instagram, our application makes it easy to get the content you want, in the quality you need.

---

## 🚀 Key Features

- **Multi-Platform Support:** Download media from popular sites like YouTube, Instagram, and more.  
- **Flexible Quality Options:** Choose from a range of video qualities (1080p, 720p, 480p, etc.) to suit your needs.  
- **Real-Time Progress Tracking:** Monitor download status with live updates via WebSocket technology.  
- **Asynchronous Processing:** Powered by `CompletableFuture`, downloads are handled asynchronously to ensure smooth and responsive user experience.  
- **Robust & Secure:** Graceful error handling and automatic cleanup for temporary files.  
- **Dockerized Deployment:** Run the application anywhere using the provided `Dockerfile` and `docker-compose.yml`.

---

## 🛠️ Technologies Used

**Backend:** Spring Boot (Java)  
**Core Tools:** yt-dlp (for parsing and downloading), FFmpeg (for media conversion and merging)  
**Frontend:** HTML, CSS, JavaScript (with SockJS and Stomp.js for WebSocket communication)  
**Containerization:** Docker, Docker Compose  

---

## 💡 How It Works

1. **Request:** The user submits a media URL through the simple web interface.  
2. **Processing:** The backend uses **yt-dlp** to fetch the media streams and **FFmpeg** to merge them into a single, high-quality file.  
3. **Real-time Updates:** Progress updates are streamed to the browser via **WebSocket** connections.  
4. **Download:** Once complete, the user gets a direct link to download the final file, which is then automatically cleaned up from the server.  

---

## 🧠 Learning Highlights

This project is a great resource for developers interested in:
- Asynchronous programming with **Spring Boot** (`CompletableFuture`)
- Integrating external CLI tools like **yt-dlp** and **FFmpeg**
- Implementing **real-time communication** with **WebSockets**
- Building a **Dockerized full-stack application**

---

## 📦 Deployment

Run the application easily using Docker:

```bash
docker-compose up --build
