#!/bin/bash
set -euo pipefail

app="$1"
device="${2:-}"
if [[ -z "$device" ]]; then
    device="$(xcrun simctl list devices booted | sed -nE '/iPhone/{s/.*\(([A-F0-9-]{36})\).*/\1/p;q;}')"
fi
if [[ -z "$device" ]]; then
    device="$(xcrun simctl list devices available | sed -nE '/iPhone/{s/.*\(([A-F0-9-]{36})\).*/\1/p;q;}')"
fi
if [[ -z "$device" ]]; then
    echo "Install an iOS simulator runtime in Xcode before running the app." >&2
    exit 1
fi

xcrun simctl bootstatus "$device" -b
xcrun simctl install "$device" "$app"
xcrun simctl launch --terminate-running-process "$device" com.tap.zoot

developer_dir="$(xcode-select -p)"
simulator_app="$developer_dir/../Applications/DeviceHub.app"
if [[ ! -d "$simulator_app" ]]; then
    simulator_app="$developer_dir/Applications/Simulator.app"
fi
open -a "$simulator_app"
