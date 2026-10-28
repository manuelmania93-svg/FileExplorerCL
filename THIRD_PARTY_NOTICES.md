# Third-party software

- Junrar 8.0.0 (https://github.com/junrar/junrar/releases/tag/v8.0.0), UnRAR license. Used only for RAR extraction. The code may not be used to develop a RAR (WinRAR) compatible archiver or re-create the proprietary RAR compression algorithm. Full license is included in the APK under assets/licenses/Junrar-UnRAR.txt.
- AndroidX Media3 1.9.4 (https://developer.android.com/media/media3), Apache License 2.0. License included under assets/licenses/Media3-Apache-2.0.txt.
- SLF4J 2.0.17 (https://www.slf4j.org/license.html), MIT License. License included under assets/licenses/SLF4J-MIT.txt.
- ZIP extraction uses java.util.zip; PDF rendering and image decoding use Android platform APIs. No cloud conversion or file upload is performed.

## Current scope and bounds

Local ZIP and RAR4/RAR5 contents and extraction. No archive creation, password entry or split-volume support. RAR dictionary budget 64 MiB; maximum 10,000 entries and 10 GB extracted bytes. Extraction uses a newly created sibling folder with unsafe paths and duplicate files rejected; failed or cancelled extraction removes only that new folder.

Internal PDF reader has previous/next page controls and pinch zoom. It is a renderer, not a PDF editor or text-search tool. Password-protected PDFs are unsupported. Picture viewer decodes an orientation-aware still frame up to 2048 pixels along the longest edge; pinch zoom and display-only rotation do not modify the source. Animated GIF/WebP show a still frame. SVG/ICO are not claimed as internal-viewer formats.

Video playback uses Media3 with Android device decoders, so a file extension alone does not guarantee playback. MP4/H.264/AAC is tested; other container/codec combinations require device support. Playback pauses when the app leaves the foreground. No network permission or streaming feature is added.

RAR4/RAR5 test archives are the small two-text-file fixtures from Junrar 8.0.0 (src/test/resources/com/github/junrar/rar4.rar and rar5.rar), under its supplied license. Other image, PDF, video and ZIP test fixtures are generated for this project and contain no user data.
