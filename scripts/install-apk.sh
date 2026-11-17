#!/usr/bin/env bash
# Build the debug APKs (the full app plus one per light profile) and get them onto the phone.
#   - If a phone is plugged in with USB debugging, installs it directly via adb (no taps).
#   - Otherwise sends it with KDE Connect; tap the notification on the phone to install.
#
# Usage:
#   ./scripts/install-apk.sh            # build + send
#   NO_BUILD=1 ./scripts/install-apk.sh # send the last build without rebuilding
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
APK_ROOT="${REPO_ROOT}/app/build/outputs/apk"
PACKAGE="com.akshay.musicplayer"

if [[ -z "${NO_BUILD:-}" ]]; then
  "${SCRIPT_DIR}/build-apk.sh"
fi

# Flavors with a debug build: "full" first, so the light profiles can find its provider.
flavors() {
  local dir
  [[ -d "${APK_ROOT}/full/debug" ]] && echo full
  for dir in "${APK_ROOT}"/*/debug; do
    [[ -d "${dir}" ]] || continue
    dir="$(basename "$(dirname "${dir}")")"
    [[ "${dir}" == full ]] || echo "${dir}"
  done
}

pick_apk() {  # $1 = flavor, $2 = ABI
  local dir="${APK_ROOT}/$1/debug"
  local apk="${dir}/app-$1-$2-debug.apk"
  [[ -f "${apk}" ]] || apk="${dir}/app-$1-universal-debug.apk"
  echo "${apk}"
}

ADB="$(command -v adb || echo "${HOME}/Android/Sdk/platform-tools/adb")"
SERIAL="$([[ -x "${ADB}" ]] && "${ADB}" devices | awk 'NR>1 && $2=="device" {print $1; exit}' || true)"

if [[ -n "${SERIAL}" ]]; then
  ABI="$("${ADB}" -s "${SERIAL}" shell getprop ro.product.cpu.abi | tr -d '\r')"
  for FLAVOR in $(flavors); do
    APK="$(pick_apk "${FLAVOR}" "${ABI}")"
    echo "Installing $(basename "${APK}") over adb on ${SERIAL}..."
    "${ADB}" -s "${SERIAL}" install -r "${APK}"
  done
  "${ADB}" -s "${SERIAL}" shell monkey -p "${PACKAGE}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  echo "Installed."
  exit 0
fi

if ! command -v kdeconnect-cli >/dev/null 2>&1; then
  echo "No adb device and kdeconnect-cli is not installed. APKs are in ${APK_ROOT}" >&2
  exit 1
fi
kdeconnect-cli --refresh >/dev/null 2>&1 || true
DEVICE_ID="$(kdeconnect-cli -a --id-only 2>/dev/null | head -n1)"
if [[ -z "${DEVICE_ID}" ]]; then
  echo "Phone not reachable over KDE Connect (is it on the same Wi-Fi with KDE Connect open?)." >&2
  echo "APKs are in ${APK_ROOT}" >&2
  exit 1
fi
for FLAVOR in $(flavors); do
  APK="$(pick_apk "${FLAVOR}" arm64-v8a)"
  kdeconnect-cli -d "${DEVICE_ID}" --share "${APK}"
  echo "Sent $(basename "${APK}") to the phone via KDE Connect - tap the notification to install."
done
