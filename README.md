# Spotifast for Android

A lightweight Android local-music player starter built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Current features
- Dark, green-accented mobile interface
- Requests permission to scan audio files indexed by Android MediaStore
- Lists local music on the device and refreshes the library
- Plays selected local tracks using ExoPlayer
- Simple now-playing panel and play/pause control
- GitHub Actions workflow builds a debug APK and uploads it as an artifact

## Build
The GitHub Actions workflow runs on pushes to `main` or `Main`, or can be started manually from the **Actions** tab:
1. Open **Actions**.
2. Select **Build Spotifast Android APK**.
3. Choose **Run workflow** if needed.
4. Open a successful run and download the `spotifast-android-debug-apk` artifact.

The APK is currently a **debug build**. It is not a signed release APK.

## Important scope notes
This initial Android version plays audio files already available on the device. It does **not yet** implement Spotify account login, Spotify catalogue streaming, Spotify Connect, background media-session controls, lyrics, downloads from streaming services, or the desktop Rust feature set. Spotify features require supported API access and appropriate user authorization. No Spotify credentials or signing secrets are included in this repository.

## Tech stack
- Kotlin
- Jetpack Compose / Material 3
- AndroidX Media3 ExoPlayer
- Gradle / GitHub Actions

## License
This Android starter is an independent mobile implementation. Review the upstream Spotifast project license before reusing any upstream code or assets.
