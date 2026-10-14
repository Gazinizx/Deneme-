# Fotoğraf Kurtar (Android)

Native Android app. No HTML or WebView.

## How it works
1. Open Google Takeout from the app.
2. Select Google Photos in Takeout and create an export.
3. Download the resulting ZIP archive(s) to the phone.
4. In the app, select the ZIP files.
5. Connect the USB drive through OTG, select a destination folder, and extract.

The app uses Android's document picker for archive and USB access. It does not ask for your Google password or directly read your Google account. It extracts common photo and video formats from Takeout ZIPs, and adds a numeric suffix when a file name already exists.

## Build
Open with Android Studio and JDK 17. GitHub Actions builds a debug APK on pushes to `main` and uploads it as an artifact.

## Limitations
Takeout export must be initiated by the user. JSON sidecar files and original folder hierarchy are not preserved; supported media files are extracted into the selected folder. Test with a small archive before processing a large export.
