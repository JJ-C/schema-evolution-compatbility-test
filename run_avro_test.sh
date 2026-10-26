#!/usr/bin/env bash
# Orchestrates the Avro version of the schema-evolution scenario:
#   Writer (v2 schema) -> Reader (v1 shape, updates age) -> Verifier.
#
# Usage:
#   ./run_avro_test.sh           # read via embedded writer schema -> PASS
#   ./run_avro_test.sh --typed   # resolve down to v1's own schema -> FAIL
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-}"
DATA_FILE="data/person.avro"

echo "== 1. Building =="
mvn -q package

JAR="target/proto-test-jar-with-dependencies.jar"
rm -f "$DATA_FILE"

echo
echo "== 2. Writer writes name/age/email =="
java -cp "$JAR" com.waltest.protoevolution.avro.Writer "$DATA_FILE"

echo
echo "== 3. Reader (v1 shape) reads it, bumps age, writes back =="
EXPECT_EMAIL="alice@example.com"
if [[ -n "$MODE" ]]; then
  java -cp "$JAR" com.waltest.protoevolution.avro.Reader "$DATA_FILE" "$MODE"
else
  java -cp "$JAR" com.waltest.protoevolution.avro.Reader "$DATA_FILE"
fi

echo
echo "== 4. Writer re-reads and verifies =="
java -cp "$JAR" com.waltest.protoevolution.avro.Verifier "$DATA_FILE" 31 "$EXPECT_EMAIL"
