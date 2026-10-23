#!/usr/bin/env bash
# Orchestrates the plain-JSON (Jackson) version of the schema-evolution
# scenario: Writer -> Reader (v1 shape, updates age) -> Verifier.
#
# Usage:
#   ./run_json_test.sh           # correct approach: generic tree mutation -> PASS
#   ./run_json_test.sh --typed   # common real bug: narrow DTO round-trip -> FAIL
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-}"
DATA_FILE="data/person.json"

echo "== 1. Building =="
mvn -q package

JAR="target/proto-test-jar-with-dependencies.jar"
rm -f "$DATA_FILE"

echo
echo "== 2. Writer writes name/age/email =="
java -cp "$JAR" com.waltest.protoevolution.json.Writer "$DATA_FILE"

echo
echo "== 3. Reader (v1 shape) reads it, bumps age, writes back =="
EXPECT_EMAIL="alice@example.com"
if [[ "$MODE" == "--typed" ]]; then
  java -cp "$JAR" com.waltest.protoevolution.json.Reader "$DATA_FILE" --typed
else
  java -cp "$JAR" com.waltest.protoevolution.json.Reader "$DATA_FILE"
fi

echo
echo "== 4. Writer re-reads and verifies =="
java -cp "$JAR" com.waltest.protoevolution.json.Verifier "$DATA_FILE" 31 "$EXPECT_EMAIL"
