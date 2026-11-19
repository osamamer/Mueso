# Repository instructions

- After every change (code, resources, dependencies, build config, docs), always finish with these steps, in order, without asking:
  1. Commit the change on `main` with a clear message.
  2. Push to `origin` (`git push`) — CI publishes a signed release from every push.
  3. Run `./scripts/install-apk.sh` to build and send the APKs to the phone (details below). It sends the APKs one by one (Mueso first, then each light profile app).
- After making code, resource, dependency, or build configuration changes, always finish by running `./scripts/install-apk.sh` (works from any directory). It builds the debug APK and puts it on the owner's phone — installed over adb if a phone is connected, otherwise sent via KDE Connect (the owner taps the notification to install). Report whether the build succeeded, where the APK was written, and whether it was installed/sent. If the phone isn't reachable, say so.
- APKs are produced under `app/build/outputs/apk/<flavor>/debug/`: `full` is Mueso itself; every other flavor is a light profile app (see `data/profiles/LightProfiles.kt`, list set by the `muesoLightProfiles` Gradle property, default `calm`).
- If a restricted execution environment blocks Gradle's cache or local socket access, rerun the same script with the required elevated build access.
