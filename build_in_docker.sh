#!/bin/bash
docker run --rm -v $(pwd):/workspace -w /workspace \
  -e ANDROID_HOME=/opt/android-sdk-linux \
  thyrlian/android-sdk:latest \
  bash -c "gradle wrapper && ./gradlew assembleDebug"
