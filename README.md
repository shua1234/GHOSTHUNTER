# Ghost Hunter v5.1

Bat counter and ghost bat ID for trail-cam video and bat call recordings. Everything runs on your own device; nothing is uploaded.

## Downloads
Open **Releases** (right side of this page) and pick:

| You have | Download |
|---|---|
| Android phone | `Ghosthunter-Mobile-v5.1.apk` |
| Windows PC | `Ghost Hunter v5.1.exe` (full desktop app), or the `desktop` folder (opens the web version in its own window with `GhostHunter.exe`) |
| Mac / anything else | open `desktop/Ghost Hunter v5.1.html` in a browser |

### Installing on Android
1. On your phone, open this repo's Releases page and tap `Ghosthunter-Mobile-v5.1.apk`.
2. Open the download. Android asks to allow installs from your browser the first time: allow it, then tap **Install**.
3. If Play Protect warns about an unknown app, tap **More details > Install anyway**.

## What's in here
- `desktop/` Ghost Hunter web v5.1 (the app is the one `.html` file), the Windows launcher, icons, and the known bat calls add-on (`Ghost Hunter files/`).
- `mobile/` Ghosthunter Mobile: an Android app that runs the same v5.1 page. To update it, replace `mobile/app/src/main/assets/index.html` with the new version, raise `versionCode` in `mobile/app/build.gradle`, and push. GitHub builds the APK automatically (**Actions** tab > Build Android app > Artifacts).

The APK is signed with a temporary key unless the repo secrets `ANDROID_KEYSTORE_B64` and `ANDROID_KEY_PASSWORD` are set. Without them, installing a newer build may need the old one uninstalled first.
