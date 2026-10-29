# OfflineShare

OfflineShare is an Android app prototype for phone-to-phone file transfer without mobile data or internet. It uses Wi-Fi Direct and a local TCP socket. Transfer payloads are encrypted with AES-GCM using a PIN-derived key and files are checksum-verified with SHA-256.

## Build the APK from a phone (GitHub Actions)

1. Create a new GitHub repository from your Android phone.
2. Upload the contents of this `offline` folder (upload the folder contents, not the ZIP itself).
3. Open the repository's **Actions** tab.
4. Select **Build OfflineShare APK** and tap **Run workflow**.
5. When the workflow finishes, open the completed run and download the **OfflineShare-debug-apk** artifact.
6. Extract the artifact and install `app-debug.apk` on two Android phones.

The workflow installs Gradle 8.10.2 and uses JDK 17, so the project does not need a Gradle wrapper in the repository.

## Important

This is a prototype. Test transfers between the exact Android devices you intend to use. Wi-Fi Direct behavior and permissions vary by Android version and manufacturer. The APK is a debug build and is not a Play Store release build.
