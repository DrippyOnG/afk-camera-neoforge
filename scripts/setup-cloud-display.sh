#!/usr/bin/env bash
set -euo pipefail
display_root=/workspace/.toolchains/display
apt_root=/workspace/.toolchains/apt
if [[ -x "$display_root/usr/bin/Xvfb" && "${1:-}" != --refresh ]]; then
    exit 0
fi
mkdir -p "$apt_root/lists/partial" "$apt_root/archives/partial" "$apt_root/packages" "$apt_root/empty.d" "$display_root"
touch "$apt_root/empty.conf"
printf '%s\n' 'deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian trixie main' > "$apt_root/sources.list"
cat > "$apt_root/apt.conf" <<EOF
Dir::Etc::parts "$apt_root/empty.d";
Dir::Etc::main "$apt_root/empty.conf";
Dir::Etc::sourcelist "$apt_root/sources.list";
Dir::Etc::sourceparts "$apt_root/empty.d";
Dir::State::lists "$apt_root/lists";
Dir::Cache::archives "$apt_root/archives";
APT::Sandbox::User "$(id -un)";
EOF
export APT_CONFIG="$apt_root/apt.conf"
/usr/bin/apt-get update
cd "$apt_root/packages"
/usr/bin/apt-get download xvfb xdotool libxdo3
for package in ./*.deb; do
    dpkg-deb -x "$package" "$display_root"
done
LD_LIBRARY_PATH="$display_root/usr/lib/x86_64-linux-gnu" "$display_root/usr/bin/xdotool" --version
