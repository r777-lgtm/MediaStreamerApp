# 🎬 ASGI Media Streamer (Android App & Server)

A seamless video streaming setup designed to stream local video files (MP4, MKV, WEBM, MOV, AVI) from a host computer (server) directly to an Android tablet or device using an open-source **LibVLC** player engine.

---

## 🛠️ Components Overview

1. **Python ASGI Server (`server.py`)**: 
   - Powered by **FastAPI** and **Uvicorn**.
   - Handles JWT-like session-based authentication (`/api/login`).
   - Automatically generates media thumbnails using `ffmpeg` (`/api/thumbnail`).
   - Provides chunked media streaming supporting HTTP range requests for smooth seeking (`/api/media`).
   - Browser-based web client interface (`index.html`) integrated as a fallback.

2. **Android Client App (`MediaStreamer`)**:
   - Built with **Kotlin** and **Jetpack Compose**.
   - Integrates **VideoLAN LibVLC SDK** (`libvlc-all`) for universal, seamless hardware-accelerated playback.
   - Automatically remembers the last successfully connected IP address using `SharedPreferences`.
   - In-app folder navigation with native back-stack support.

---

## ⚙️ Server Setup & Deployment

### Prerequisites
- Python 3.8+
- `ffmpeg` installed and added to your system's PATH (required for thumbnail generation).

### 1. Installation
Clone your repository and install the backend dependencies:

```bash
pip install fastapi uvicorn pydantic
