#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
# ----------------------------------------------------------------------------

set -e

# Find the project base dir, i.e. the directory that contains the folder ".mvn".
find_maven_basedir() {
  WDIR=$(pwd)
  while true; do
    if [ -d "$WDIR"/.mvn ]; then
      BASE_DIR=$WDIR
      break
    fi
    if [ "$WDIR" = "/" ]; then
      BASE_DIR=$(pwd)
      break
    fi
    WDIR=$(cd "$WDIR/.." && pwd)
  done
  echo "$BASE_DIR"
}

BASE_DIR=$(find_maven_basedir)
MAVEN_WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
MAVEN_HOME_PARENT="${MAVEN_USER_HOME}/wrapper/dists"

# Extract filename and folder name from URL
DIST_FILENAME="${DISTRIBUTION_URL##*/}"
DIST_NAME="${DIST_FILENAME%-bin.zip}"
MAVEN_HOME="$MAVEN_HOME_PARENT/$DIST_NAME/$DIST_NAME"

if [ ! -d "$MAVEN_HOME" ]; then
  mkdir -p "$MAVEN_HOME_PARENT/$DIST_NAME"
  echo "Downloading Apache Maven $DIST_NAME..."
  TMP_DOWNLOAD_DIR=$(mktemp -d)
  if command -v curl > /dev/null; then
    curl -fsSL -o "$TMP_DOWNLOAD_DIR/$DIST_FILENAME" "$DISTRIBUTION_URL"
  elif command -v wget > /dev/null; then
    wget -q -O "$TMP_DOWNLOAD_DIR/$DIST_FILENAME" "$DISTRIBUTION_URL"
  else
    echo "ERROR: Neither curl nor wget is available. Cannot download Maven." >&2
    exit 1
  fi
  unzip -q "$TMP_DOWNLOAD_DIR/$DIST_FILENAME" -d "$MAVEN_HOME_PARENT/$DIST_NAME"
  rm -rf "$TMP_DOWNLOAD_DIR"
  echo "Done."
fi

MAVEN_CMD="$MAVEN_HOME/bin/mvn"
exec "$MAVEN_CMD" "$@"