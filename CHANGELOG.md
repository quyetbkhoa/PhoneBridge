# Changelog

All notable PhoneBridge changes are documented here. Versions follow Semantic Versioning.

## [Unreleased]

### Fixed

- Stream remote stdout/stderr into the Terminal while a command is still running.
- Explain long-lived shell sessions after eight seconds instead of showing an unexplained endless spinner.
- Preserve a clean cancellation result when STOP closes the USB transport.

### Added

- Android USB Host discovery for ADB interfaces and bulk endpoints.
- Native ADB packet transport, RSA authorization, persistent keys, and shell v2 parsing.
- Compose UI for connection, terminal, presets, history, settings, logs, and updates.
- Remote Shizuku startup, multiline commands, and `.sh` import through Android SAF.
- GitHub Releases updater with SHA-256 verification and Android package installer handoff.
- GitHub Actions CI and tag-driven APK release automation.

## [0.1.0] - 2026-08-14

### Added

- Initial PhoneBridge preview foundation.
