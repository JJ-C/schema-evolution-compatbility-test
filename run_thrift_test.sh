#!/usr/bin/env bash
# Orchestrates the Thrift version of the schema-evolution scenario:
#   Writer (v2 IDL) -> Reader (v1 IDL, updates age) -> Verifier.
#
# Usage:
#   ./run_thrift_test.sh                   # ordinary generated struct -> FAIL (lossy BY DEFAULT)
#   ./run_thrift_test.sh --manual-preserve  # hand-rolled generic struct -> PASS
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-}"
DATA_FILE="data/person.thrift.bin"

echo "== 1. Building =="
mvn -q package

JAR="target/proto-test-jar-with-dependencies.jar"
rm -f "$DATA_FILE"

echo
echo "== 2. Writer writes name/age/email =="
java -cp "$JAR" com.waltest.protoevolution.thrift.Writer "$DATA_FILE"

echo
echo "== 3. Reader (v1 IDL) reads it, bumps age, writes back =="
EXPECT_EMAIL="alice@example.com"
if [[ -n "$MODE" ]]; then
  java -cp "$JAR" com.waltest.protoevolution.thrift.Reader "$DATA_FILE" "$MODE"
else
  java -cp "$JAR" com.waltest.protoevolution.thrift.Reader "$DATA_FILE"
fi

echo
echo "== 4. Writer re-reads and verifies =="
java -cp "$JAR" com.waltest.protoevolution.thrift.Verifier "$DATA_FILE" 31 "$EXPECT_EMAIL"
