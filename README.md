# PhoneBridge

PhoneBridge is an Android developer utility that lets one Android phone act as a USB ADB host for another Android phone. It talks directly to the remote phone's `adbd` through Android's USB Host API—no PC-side `adb` binary, root, wireless debugging, Accessibility service, or Shizuku installation on the host is required.

```text
Phone A (USB host)
        ↓ USB-C data cable
ADB protocol over USB bulk endpoints
        ↓
Phone B (USB debugging enabled)
```

## Features

- Detects the standard ADB USB interface (`0xff/0x42/0x01`) and Bulk IN/OUT endpoints.
- Requests Android USB permission and handles attach/detach cleanup.
- Implements ADB `CNXN`, `AUTH`, `OPEN`, `OKAY`, `WRTE`, and `CLSE` packets.
- Generates a 2048-bit ADB RSA key once, stores it in private app storage, and supports the remote authorization dialog.
- Runs real multiline remote shell commands with `shell,v2` stdout, stderr, and exit status; falls back to legacy `shell:` with an unavailable exit code.
- Imports `.sh` files with Android's Storage Access Framework, previews/edits them, and streams script content to remote `sh`.
- Saves, edits, renames, duplicates, deletes, runs, and reorders command presets.
- Keeps command history and bounded developer logs without logging the private key.
- Starts Shizuku after detecting its script in common external-storage paths.
- Supports optional auto-connect, auto-start Shizuku, and one preset auto-run per USB attachment.
- Checks this repository's GitHub Releases, downloads the release APK and checksum, verifies SHA-256, then opens the standard Android installer.

## Requirements

- Two Android phones and a USB-C cable that supports data.
- The PhoneBridge host phone must negotiate the USB host role and support Android USB Host.
- USB debugging must be enabled on the remote phone.
- Android 7.0 (API 24) or newer on the host; current Android versions are recommended.

PhoneBridge cannot force USB role negotiation through public Android APIs. If no device is found, change the USB connection mode or reverse/reconnect the cable and confirm which phone is acting as host.

## Usage

1. Install PhoneBridge on the phone that will act as host.
2. Enable Developer options and USB debugging on the remote phone.
3. Connect both phones with a USB-C data cable.
4. Open PhoneBridge and tap **CONNECT**.
5. Grant USB permission on the host.
6. On the remote phone, accept the RSA fingerprint dialog and optionally select **Always allow**.
7. Use Home, Terminal, Presets, or **START SHIZUKU**.

## Shizuku startup

The dedicated action checks these paths before execution:

- `/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
- `/sdcard/Android/data/moe.shizuku.privileged.api/start.sh`

The command's real stdout, stderr, and exit code are displayed. A missing script is reported rather than silently treated as success.

## Custom commands and scripts

Terminal accepts a single command or a multiline script. **IMPORT .SH** uses Android SAF, so PhoneBridge does not assume that the selected local path exists on the remote phone. On `shell,v2`, the content is sent to remote `sh` over stdin. Legacy devices use a collision-safe heredoc fallback.

## Presets and history

Presets are stored in Android DataStore and include a built-in **Start Shizuku** preset in addition to the dedicated Home action. The History screen records command text, output, nullable exit status, execution time, and duration.

## App updater

Settings → App Update queries `quyetbkhoa/PhoneBridge` through the GitHub Releases API. PhoneBridge accepts assets only from the configured repository, verifies the companion `.apk.sha256`, and uses Android's normal package installer. Android may first require **Allow from this source**.

## Build locally

Use Android Studio's bundled JDK or JDK 17 and an Android SDK containing API 37:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds use `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in GitHub Actions when configured. Until production secrets exist, the release variant is signed with the standard debug key so preview APKs remain installable; no keystore or password is committed.

## GitHub Releases

Every push and pull request runs tests, lint, and a Debug APK build. Tags matching `v*` run the release workflow. The tag must match `VERSION_NAME`, and the workflow creates:

```text
PhoneBridge-vX.Y.Z.apk
PhoneBridge-vX.Y.Z.apk.sha256
```

## Known limitations

- USB-C role negotiation differs by vendor and cable; a non-root app cannot force the host role using public APIs.
- Only one command stream is executed at a time in the current transport.
- Stopping a command closes the USB transport to guarantee cancellation; reconnect before the next command.
- Physical two-phone USB behavior and vendor authorization dialogs require device testing and cannot be fully covered by JVM tests.
