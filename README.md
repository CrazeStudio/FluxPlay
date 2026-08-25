# Fluxplay 2.0 (Android)

Fluxplay is a modern video streaming player and multi-source media discovery app built with Kotlin and Jetpack Compose.

## Features

- **Instant Video Streaming**: Stream direct video URLs (MP4, HLS, DASH, WebM) powered by ExoPlayer (Media3).
- **Gestures & Controls**: Double-tap left to rewind 10s, double-tap right to skip forward 10s, single-tap toggle controls, playback speed selector (0.5x to 2.0x), full-screen landscape immersion, audio mute toggle, and interactive scrubber with buffer indicator.
- **Buffer for Seeking**: Direct HTTP stream caching engine that downloads media chunks in the background so seeking and scrubbing work smoothly on any server.
- **Unified Multi-Source Discover**:
  - **TMDB (The Movie Database)**: In-theaters / Now playing films and live search.
  - **iTunes Movies**: Top digital movies and full search with high-res artwork.
  - **AniList (GraphQL)**: Trending and searchable anime with Japanese titles, studios, score, episode count, and cast.
  - **TVmaze**: Popular and searchable TV shows with synopsis, genres, and cast.
  - **Letterboxd**: Public user activity RSS feed and official search integration.
- **Rich Media Details**: High-resolution posters, synopsis, genres chips, studios, cast list, source links, and instant one-tap trailer streaming.
- **Watch History & Bookmarks**: SQLite local persistence powered by Room database with resume-at-last-position playback.
- **Theme & Appearance Customization**: Custom primary & accent glow color pickers, light/dark/system themes.
- **Backup & Restore**: Export history and bookmarks to JSON, or restore from previous backups.

## Tech Stack

- **Kotlin 2.0**
- **Jetpack Compose & Material 3**
- **ExoPlayer (AndroidX Media3)**
- **Room Database** with KSP
- **Coil** for image loading
- **OkHttp** for networking and GraphQL
- **Kotlinx Serialization**
