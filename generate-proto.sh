#!/usr/bin/env bash
# Generates Java sources from both the v1 and v2 .proto schemas into
# src/main/java. Re-run this any time you tweak the .proto files.
set -euo pipefail

cd "$(dirname "$0")"

OUT=src/main/java
mkdir -p "$OUT"

protoc --proto_path=proto/v1 --java_out="$OUT" proto/v1/person.proto
protoc --proto_path=proto/v2 --java_out="$OUT" proto/v2/person.proto

echo "Generated:"
find "$OUT" -name 'PersonProto.java'
