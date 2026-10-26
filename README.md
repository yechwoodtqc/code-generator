# YW Code Generator

A small Android app for generating one-time YW App Store activation codes.

## Features
- Generate between 1 and 50 codes per request.
- Set expiry from 1 to 720 hours (default: 168 hours / 7 days).
- Copy any generated code with one tap.
- Supports Android 6.0 (API 23) and newer.

## Use
1. Install the APK from the **Build Code Generator APK** workflow artifact.
2. Enter the existing Cloudflare Worker `ADMIN_SECRET`.
3. Choose the count and expiry, then tap **Generate code**.
4. Copy a code and enter it into YW app installer on the flip phone.

The app calls the existing HTTPS endpoint:
`https://yw-app-store-api.yw-app-installer-store-2026.workers.dev/admin/codes`

## Security
- The admin secret is not embedded in the APK or saved to app storage.
- It is sent as a Bearer token only to the configured HTTPS Worker.
- Generated codes are single-use; they expire according to the selected TTL.
- Only install this administrator tool on a device you control.

## Build
Pushes to `main` build a debug APK in GitHub Actions. Open **Actions → Build Code Generator APK** and download the `yw-code-generator-debug` artifact from a successful run.
