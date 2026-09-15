#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties")
ARCHIVE=${DIST_URL##*/}
VERSION=$(printf '%s' "$ARCHIVE" | sed -n 's/apache-maven-\(.*\)-bin.zip/\1/p')
CACHE_DIR="${HOME}/.m2/wrapper/dists/ai-contract-test"
MAVEN_HOME="$CACHE_DIR/apache-maven-$VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$CACHE_DIR"
  curl -fsSL "$DIST_URL" -o "$CACHE_DIR/$ARCHIVE"
  unzip -q -o "$CACHE_DIR/$ARCHIVE" -d "$CACHE_DIR"
  rm -f "$CACHE_DIR/$ARCHIVE"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"

