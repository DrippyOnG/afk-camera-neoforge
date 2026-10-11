#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
toolchain_dir=/workspace/.toolchains/jdk25
if [[ ! -x "$toolchain_dir/bin/javac" ]]; then
    if [[ -d "$toolchain_dir" && -n "$(ls -A "$toolchain_dir")" ]]; then
        printf '%s\n' 'Existing incomplete Java toolchain; inspect it before replacing it.' >&2
        exit 1
    fi
    download_dir=$(mktemp -d)
    trap 'rm -rf "$download_dir"' EXIT
    jdk_url=https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz
    curl --fail --location --retry 3 "$jdk_url" -o "$download_dir/jdk.tar.gz"
    curl --fail --location --retry 3 "$jdk_url.sha256" -o "$download_dir/jdk.sha256"
    checksum=$(tr -d '[:space:]' < "$download_dir/jdk.sha256")
    [[ "$checksum" =~ ^[a-fA-F0-9]{64}$ ]] || { printf '%s\n' 'Invalid official JDK checksum' >&2; exit 1; }
    printf '%s  %s\n' "$checksum" "$download_dir/jdk.tar.gz" | sha256sum --check --status
    mkdir -p "$toolchain_dir"
    tar -xzf "$download_dir/jdk.tar.gz" --strip-components=1 -C "$toolchain_dir"
    rm -rf "$download_dir"
    trap - EXIT
fi
export JAVA_HOME="$toolchain_dir"
"$JAVA_HOME/bin/javac" -version
exec ./scripts/cloud-gradle.sh build
