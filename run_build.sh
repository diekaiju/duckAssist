#!/bin/bash
export PATH="$HOME/gradlew-fdroid:$PATH"
cd ~/fdroid-workspace
# Clear status files and logs
rm -f repo/status/*
rm -f repo/*.log.gz
/usr/bin/fdroid build --verbose --no-tarball --no-refresh --all
