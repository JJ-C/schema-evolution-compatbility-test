#!/usr/bin/env bash
# Runs all 10 schema-evolution test scenarios across all 4 formats and
# prints a single PASS/FAIL summary table. Exits non-zero if any
# scenario's actual result does not match its expected result.
set -uo pipefail
cd "$(dirname "$0")"

declare -a RESULTS=()
TOTAL_FAILURES=0

# run_case <label> <expected: PASS|FAIL> <script> [args...]
run_case() {
  local label="$1"
  local expected="$2"
  shift 2

  if "$@" > /tmp/schema_evolution_test_output.log 2>&1; then
    actual="PASS"
  else
    actual="FAIL"
  fi

  if [[ "$actual" == "$expected" ]]; then
    RESULTS+=("OK    $label (expected $expected, got $actual)")
  else
    RESULTS+=("WRONG $label (expected $expected, got $actual)")
    TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
  fi
}

echo "Running all schema evolution scenarios. This builds the project once per format."
echo

run_case "Protobuf / default"            PASS ./run_test.sh
run_case "Protobuf / --discard"          FAIL ./run_test.sh --discard
run_case "Ion / default"                 PASS ./run_ion_test.sh
run_case "Ion / --typed"                 FAIL ./run_ion_test.sh --typed
run_case "JSON / default"                PASS ./run_json_test.sh
run_case "JSON / --typed"                FAIL ./run_json_test.sh --typed
run_case "JSON / --overflow"             PASS ./run_json_test.sh --overflow
run_case "CBOR / default"                PASS ./run_cbor_test.sh
run_case "CBOR / --typed"                FAIL ./run_cbor_test.sh --typed
run_case "CBOR / --overflow"             PASS ./run_cbor_test.sh --overflow

echo "===================================================================="
echo "SUMMARY"
echo "===================================================================="
for line in "${RESULTS[@]}"; do
  echo "$line"
done
echo "===================================================================="

if [[ "$TOTAL_FAILURES" -gt 0 ]]; then
  echo "$TOTAL_FAILURES scenario(s) did not match their expected result."
  exit 1
fi

echo "All 10 scenarios matched their expected result."
