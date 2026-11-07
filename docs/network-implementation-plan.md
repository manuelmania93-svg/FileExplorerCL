# Network implementation preparation

This branch is separate from the Library review. No account is connected, no server is started, and no user files are exposed.

## Existing source

Network is currently a placeholder. INTERNET already exists for the updater. The README's statement that the app has no network permission is outdated.

## Native cloud connections

- Dropbox: native PKCE, app key and registered redirect, full-storage metadata/read scopes for browsing. Public deployment needs developer-console setup and distribution review.
- Google Drive: Android AuthorizationClient and registered package/signing certificate. Full-drive read access is a restricted scope. The narrower drive.file scope does not grant full-drive browsing.
- OneDrive: MSAL native public client, Entra app registration, explicit personal/work account audience and signature-bound redirect.
- Box: documented code exchange requires a client secret. Never embed that secret in this public APK. A backend token exchange is a separate hosting/privacy commitment, not something silently added. Legacy Android SDK is unsupported.

No client IDs/app registrations were found in current source. No provider should show Connected or a fake file listing without successful authorization and a verified API response. Implement real list/download/upload operations only after credential configuration and end-to-end test access are available. Do not log tokens or authorization codes. Store refresh credentials under Android Keystore-backed encryption; exclude credentials from backup; disconnect clears local credentials and offers provider revocation.

Android SAF is an optional installed-provider bridge, not a replacement for the requested native connections.

## Phone access from another device

The reference screenshot matches a documented FTP workflow. Before implementation is considered ready:

- Explicit Start only, never startup/background auto-start or port forwarding.
- User-selected export root, canonical-path containment, no traversal/symlink escape.
- Authentication required. Random per-session credential default, never logs or analytics. Hidden files off by default. No anonymous mode.
- Persistent foreground notification with Stop; stop closes all sockets and invalidates credential. Test stop/background lifecycle.
- Local interface only, reject public/metered interfaces. Do not activate on the owner's phone as part of development.
- Plain FTP exposes credentials and data on the LAN. Explain this before enabling; secure transport is preferable. Don't silently claim FTP is encrypted.
- First build should be read-only unless file mutation support is expressly scoped, with a clear UI label. File writes/deletion require stronger review and separate tests.

## Acceptance evidence

Cloud: registration validation, user-cancel, state/PKCE/replay rejection, real folder pagination, byte-exact downloads, explicit upload confirmation, expiry/refresh/revocation, network failure, separate accounts, no credentials in APK/logs/backup.

Phone service: authenticated fixture-only listing and byte download, bad password rejected, traversal and symlink escape blocked, hidden policy, concurrency/size limits, stop invalidation, background lifecycle, no auto-start, actual second-device LAN check before claiming cross-device proof.

## Sources

- https://docs.dropboxapi.com/dropbox-api/docs/get-started/authorization
- https://docs.dropboxapi.com/dropbox-api/docs/get-started/tutorial/app-console
- https://developer.android.com/identity/authorization
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://learn.microsoft.com/en-us/entra/identity-platform/tutorial-mobile-app-android-prepare-app
- https://developer.box.com/guides/authentication/oauth2/
- https://developer.box.com/guides/mobile/android
- https://github.com/box/box-swift-sdk-gen/issues/540
- https://developer.android.com/guide/topics/providers/document-provider
- https://accessibleandroid.com/an-easy-method-to-wirelessly-access-manage-androids-files-and-folders-on-windows/
- https://developer.android.com/privacy-and-security/local-network-permission
