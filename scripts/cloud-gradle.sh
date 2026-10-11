#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /workspace/.toolchains/jdk25/bin/java ]]; then
    export JAVA_HOME=/workspace/.toolchains/jdk25
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle-afk}"

# Java does not automatically use the cloud's HTTPS_PROXY or system CA store.
# Preserve TLS validation and never copy proxy credentials into repository files.
proxy_options=$(python3 - <<'PY'
import os, re, urllib.parse
value = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
if value:
    proxy = urllib.parse.urlparse(value)
    if proxy.username or proxy.password:
        raise SystemExit('Authenticated proxy URLs need supported Java proxy authentication configuration.')
    if not proxy.hostname or not re.fullmatch(r'[A-Za-z0-9.-]+', proxy.hostname):
        raise SystemExit('Unsupported proxy hostname')
    port = proxy.port or 80
    print(f'-Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={port} '
          f'-Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={port}')
PY
)
if [[ -n "$proxy_options" ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $proxy_options"
    if [[ -r /etc/ssl/certs/java/cacerts ]]; then
        export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
    fi
fi
exec ./gradlew --no-daemon --console=plain "$@"
