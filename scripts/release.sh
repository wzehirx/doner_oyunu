#!/bin/bash
# Kullanım: scripts/release.sh <sürüm-etiketi>   (örn. v0.2)
set -e
cd "$(dirname "$0")/.."
export ANDROID_HOME=/opt/android-sdk
TAG=${1:?sürüm etiketi gerekli}
gradle -q assembleDebug assembleRelease --no-daemon 2>&1 | grep -v JAVA_TOOL || true
mkdir -p releases
cp app/build/outputs/apk/debug/app-debug.apk "releases/doner-$TAG-debug.apk"
cp app/build/outputs/apk/release/app-release.apk "releases/doner-$TAG-release.apk"
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs "releases/doner-$TAG-release.apk" | head -1
$ANDROID_HOME/build-tools/34.0.0/apksigner verify "releases/doner-$TAG-debug.apk" && echo "debug imza OK"
ls -la releases
