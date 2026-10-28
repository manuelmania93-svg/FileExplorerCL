# Stock File Explorer

A CX-inspired Android file manager in our own style. Shows **every file in every
folder** - including documents that other file managers hide from the Downloads
folder.

## Why

Some file managers filter the Download folder through the media database, so
PDFs, Word files and other documents simply do not appear. Stock File Explorer
reads folders directly from the filesystem (with Android's "All files access"
permission), so nothing is missing.

## Features

- Storage dashboard: usage ring, per-category sizes (images, videos, audio,
  documents, apps, archives)
- File browser: list/grid view, breadcrumbs, sorting (name/date/size/type),
  hidden-files toggle, image thumbnails, multi-select
- File actions: open, share, rename, delete, new folder
- Library view: all files of a category across the whole storage
- English and German, follows the system language
- No ads, no tracking, no network permission - fully offline
- Tabs for Local / Library / Network (network storage is planned)

## Install

1. Download `app-debug.apk` from the latest successful
   [Actions](../../actions) run (artifact `StockFileExplorer-Debug-APK`).
2. Allow installs from unknown sources when Android asks.
3. On first start, grant "All files access" so every file type shows up.

## Build

```
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK (platform android-35).
