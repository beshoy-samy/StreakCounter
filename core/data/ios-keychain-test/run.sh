#!/bin/sh
# Runs the Keychain tests of a Kotlin/Native test binary on the iOS simulator.
#
# The simulator only grants Keychain access to an installed app whose binary carries entitlements
# (a bare test executable gets errSecNotAvailable -25291, an app without them errSecMissingEntitlement
# -34018). This wraps the test binary in a minimal .app, installs it, runs the filtered tests and
# fails unless all of them pass.
#
# Usage: run.sh <test.kexe> <simulator device name> <work dir> <gtest filter>
set -eu

KEXE=$1
DEVICE=$2
WORK=$3
FILTER=$4
BUNDLE_ID=com.streakcounter.core.data.test
APP="$WORK/KeychainTest.app"

rm -rf "$APP"
mkdir -p "$APP"
cp "$KEXE" "$APP/KeychainTest"
cat > "$APP/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>$BUNDLE_ID</string>
<key>CFBundleExecutable</key><string>KeychainTest</string>
<key>CFBundleName</key><string>KeychainTest</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>1.0</string>
<key>LSRequiresIPhoneOS</key><true/>
</dict></plist>
PLIST
codesign --force --sign - "$APP" >/dev/null

BOOTED_HERE=false
if ! xcrun simctl list devices booted | grep -q "$DEVICE ("; then
    # Another task may boot it at the same time; bootstatus below waits either way.
    if xcrun simctl boot "$DEVICE" 2>/dev/null; then BOOTED_HERE=true; fi
fi
xcrun simctl bootstatus "$DEVICE" -b >/dev/null

cleanup() {
    xcrun simctl uninstall "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1 || true
    if [ "$BOOTED_HERE" = true ]; then xcrun simctl shutdown "$DEVICE" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

xcrun simctl install "$DEVICE" "$APP"
OUTPUT=$(xcrun simctl launch --console-pty --terminate-running-process "$DEVICE" "$BUNDLE_ID" \
    --ktest_filter="$FILTER" 2>&1 | tr -d '\r')
echo "$OUTPUT"

# simctl launch does not return the test exit code: require a PASSED line, no FAILED line and >0 tests.
echo "$OUTPUT" | grep -q '\[  PASSED  \] [1-9][0-9]* tests\.' || exit 1
if echo "$OUTPUT" | grep -q '\[  FAILED  \]'; then exit 1; fi
