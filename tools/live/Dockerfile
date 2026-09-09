# D3 live runner: reproducible Forge 1.20.1 (1.20.1-47.2.0) proof environment.
#
# This image runs tools/run-live.sh (JDK 17: --release 8 build, server
# runtime), NOT tools/check.sh stage 1 (which needs any modern javac for
# --release). JAVA17_HOME points at the image JDK.
#
# The image runs as a non-root `builder` user so bind-mounted checkouts
# (D3_DIR, /w) keep host ownership — root-owned build/ leftovers poison
# the shared cache. Match the host user at build time (--build-arg UID/GID
# below); a conflicting id fails the build loudly, never silently.
#
# Build (from a checkout parent holding the sibling repos):
#   docker build -f bridge-1201/tools/live/Dockerfile \
#     --build-arg UID=$(id -u) --build-arg GID=$(id -g) -t matou-live-1201 .
#
# First run (network once to provision D3_DIR cache):
#   docker run --rm -v "$PWD:/w" -v matou-d3:/tmp/matou-d3-live \
#     -w /w/bridge-1201 matou-live-1201
#
# Offline re-run (cache already provisioned):
#   docker run --rm -v "$PWD:/w" -v matou-d3:/tmp/matou-d3-live \
#     -e D3_OFFLINE=1 -w /w/bridge-1201 matou-live-1201
#
# Root-owned `build/` leftovers in an old cache must be cleared once
# (rm or fresh D3_DIR): run-live.sh refuses to build over them loudly.
#
# No machine paths inside the image: Java 17 + python3 + curl + git +
# ripgrep only (ripgrep serves the check.sh contamination gates: without it
# they would pass vacuously — never green by omission).
# (git serves the release guards: VERSION + clean-tree checks).
# Pins live in tools/run-live.sh, not here.
# Repro pin (measured 2026-09-09 via docker pull): same tag == same bytes.
# Refresh deliberately, never silently.
FROM eclipse-temurin:17-jdk-jammy@sha256:c8e512c273fb824f1c48dafa8121ee6b4ba1fd20437165f75dbbb7c96e0dfdf5
RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 curl ca-certificates git ripgrep \
  && rm -rf /var/lib/apt/lists
ENV JAVA17_HOME=/opt/java/openjdk
ARG UID=1000
ARG GID=1000
RUN groupadd -g "$GID" builder \
  && useradd -m -u "$UID" -g "$GID" -s /bin/bash builder \
  && mkdir -p /w/bridge-1201 && chown builder:builder /w /w/bridge-1201
USER builder
WORKDIR /w/bridge-1201
CMD ["sh", "tools/run-live.sh"]
