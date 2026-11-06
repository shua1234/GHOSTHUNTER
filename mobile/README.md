# Ghosthunter Mobile v5.1 for Android (.apk)

The Ghost Hunter bat counter and ghost bat ID as an Android app. It runs the same v5.1 app as the desktop version, and everything stays on your phone; nothing is uploaded.

Works on Android 7.0 or newer.

## Install on your phone
1. Download **`Ghosthunter-Mobile-v5.1.apk`** to your phone.
2. Open the download. The first time, Android asks to allow installs from your browser or Files app: allow it, then tap **Install**.
3. If Play Protect warns about an unknown app, tap **More details → Install anyway**. This happens because the app isn't from the Play Store.
4. Open **Ghosthunter** from your app list.

### Where to get the APK
- **[Releases → v5.1](https://github.com/shua1234/GHOSTHUNTER/releases/tag/v5.1)**: tap **`Ghosthunter-Mobile-v5.1.apk`**.
- Or the **Actions** tab → **Build Android app** → the latest run with a green tick → under **Artifacts**, download **Ghosthunter-Mobile-APK**. It comes as a zip; unzip it to get the `.apk`.

## Updating the app (for Josh)
1. Replace `app/src/main/assets/index.html` with the new version of the app.
2. In `app/build.gradle`, raise `versionCode` (for example 5101 → 5201) and set `versionName` (for example "5.2").
3. Push to GitHub. The **Build Android app** workflow builds the new APK automatically.

The APK is signed with a temporary key unless the repo secrets `ANDROID_KEYSTORE_B64` and `ANDROID_KEY_PASSWORD` are set. Without them, installing a newer build may mean uninstalling the old one first.

Looking for the Windows version? See [`desktop/README.md`](../desktop/README.md).
