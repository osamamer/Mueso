# Repository instructions

- After making code, resource, dependency, or build configuration changes, always finish by running `./scripts/install-apk.sh` (works from any directory). It builds the debug APK and puts it on the owner's phone — installed over adb if a phone is connected, otherwise sent via KDE Connect (the owner taps the notification to install). Report whether the build succeeded, where the APK was written, and whether it was installed/sent. If the phone isn't reachable, say so.
- The APK is produced under `app/build/outputs/apk/debug/`.
- If a restricted execution environment blocks Gradle's cache or local socket access, rerun the same script with the required elevated build access.
