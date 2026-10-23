#!/usr/bin/env bash
# Orchestrates the full proto-evolution scenario:
#   Writer(v2) -> Reader(v1, updates age) -> Verifier(v2) checks result
#
# Usage:
#   ./run_test.sh            # happy path (unknown fields preserved)
#   ./run_test.sh --discard  # negative control (reader drops unknown fields)
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-}"
DATA_FILE="data/person.bin"

echo "== 1. Generating proto sources =="
./generate-proto.sh

echo
echo "== 2. Building =="
mvn -q package

JAR="target/proto-test-jar-with-dependencies.jar"
rm -f "$DATA_FILE"

echo
echo "== 3. Writer (v2 schema) writes name/age/email =="
java -cp "$JAR" com.waltest.protoevolution.Writer "$DATA_FILE"

echo
echo "== 4. Reader (v1 schema) reads it, bumps age, writes back =="
# Email should ALWAYS still be there regardless of mode -- that's the
# whole point. --discard exists to prove the test harness actually
# catches a broken implementation instead of rubber-stamping PASS.
EXPECT_EMAIL="alice@example.com"
if [[ "$MODE" == "--discard" ]]; then
  java -cp "$JAR" com.waltest.protoevolution.Reader "$DATA_FILE" --discard
else
  java -cp "$JAR" com.waltest.protoevolution.Reader "$DATA_FILE"
fi

echo
echo "== 5. Writer (v2 schema) re-reads and verifies =="
java -cp "$JAR" com.waltest.protoevolution.Verifier "$DATA_FILE" 31 "$EXPECT_EMAIL"
