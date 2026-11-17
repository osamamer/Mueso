#!/usr/bin/env bash
# Build the debug APK and get it onto the phone.
#   - If a phone is plugged in with USB debugging, installs it directly via adb (no taps).
#   - Otherwise sends it with KDE Connect; tap the notification on the phone to install.
#
# Usage:
#   ./scripts/install-apk.sh            # build + send
#   NO_BUILD=1 ./scripts/install-apk.sh # send the last build without rebuilding
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
APK_DIR="${REPO_ROOT}/app/build/outputs/apk/debug"
APK_PREFIX="app"
APK_SUFFIX="debug"
PACKAGE="com.akshay.musicplayer"

if [[ -z "${NO_BUILD:-}" ]]; then
  "${SCRIPT_DIR}/build-apk.sh"
fi

pick_apk() {  # $1 = ABI
  local apk="${APK_DIR}/${APK_PREFIX}-$1-${APK_SUFFIX}.apk"
  [[ -f "${apk}" ]] || apk="${APK_DIR}/${APK_PREFIX}-universal-${APK_SUFFIX}.apk"
  echo "${apk}"
}

ADB="$(command -v adb || echo "${HOME}/Android/Sdk/platform-tools/adb")"
SERIAL="$([[ -x "${ADB}" ]] && "${ADB}" devices | awk 'NR>1 && $2=="device" {print $1; exit}' || true)"

if [[ -n "${SERIAL}" ]]; then
  ABI="$("${ADB}" -s "${SERIAL}" shell getprop ro.product.cpu.abi | tr -d '\r')"
  APK="$(pick_apk "${ABI}")"
  echo "Installing $(basename "${APK}") over adb on ${SERIAL}..."
  "${ADB}" -s "${SERIAL}" install -r "${APK}"
  "${ADB}" -s "${SERIAL}" shell monkey -p "${PACKAGE}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  echo "Installed."
  exit 0
fi

if ! command -v kdeconnect-cli >/dev/null 2>&1; then
  echo "No adb device and kdeconnect-cli is not installed. APK is in ${APK_DIR}" >&2
  exit 1
fi
kdeconnect-cli --refresh >/dev/null 2>&1 || true
DEVICE_ID="$(kdeconnect-cli -a --id-only 2>/dev/null | head -n1)"
if [[ -z "${DEVICE_ID}" ]]; then
  echo "Phone not reachable over KDE Connect (is it on the same Wi-Fi with KDE Connect open?)." >&2
  echo "APK is in ${APK_DIR}" >&2
  exit 1
fi
APK="$(pick_apk arm64-v8a)"
kdeconnect-cli -d "${DEVICE_ID}" --share "${APK}"
echo "Sent $(basename "${APK}") to the phone via KDE Connect - tap the notification to install."
