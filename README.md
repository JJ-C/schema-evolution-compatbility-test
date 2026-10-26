# Schema Evolution Compatibility Tests

Automated tests validating forward/backward-compatible schema
evolution across four serialization formats: Protocol Buffers,
Amazon Ion, JSON, and CBOR.

## 1. Objective

Validate the following compatibility contract:

1. A **writer** serializes a message using a schema version that
   includes a field (`email`) which did not exist in an earlier
   schema version.
2. A **reader**, still built against the earlier schema version
   (unaware of `email`), deserializes that message, modifies an
   **existing** field (`age`), and re-serializes the message to the
   same location.
3. The writer re-reads the file. The field it added (`email`) must
   still be present and unchanged, and the field the reader modified
   (`age`) must reflect the update.

This models a real deployment scenario: two services built from
different versions of the same schema, exchanging data through a
shared file or message bus, deployed independently of one another.

Each format is tested in multiple **modes** — distinct
implementation strategies the reader could use. Only some modes
satisfy the contract in step 3; others fail it. Both outcomes are
intentional and are asserted by the test harness (`Verifier`), not
inferred from logs.

## 2. Results Summary

10 total test runs across 4 formats. `./run_all.sh` executes all of
them and reports a PASS/FAIL summary table, with a non-zero exit
code if any scenario's actual result does not match its expected
result.

| Format | Reader Implementation Mode | Command | Result | Why |
|---|---|---|---|---|
| Protobuf | Default (generated Builder) | `./run_test.sh` | **PASS** | Wire format stores fields by tag number; the generated `Builder` carries unrecognized tags forward automatically. |
| Protobuf | Explicit discard of unknown fields | `./run_test.sh --discard` | **FAIL** | Negative control. Proves the harness fails when unknown-field preservation is intentionally defeated. |
| Amazon Ion | Generic struct, path-scoped update | `./run_ion_test.sh` | **PASS** | Document is held as a generic `IonStruct` tree; only the `age` key is mutated in place, so untouched keys are re-emitted unchanged. |
| Amazon Ion | Bind to narrow typed record | `./run_ion_test.sh --typed` | **FAIL** | Document is deserialized into a Java record containing only `name`/`age`. The output is reconstructed from that record, which has no field to carry `email`. |
| JSON (Jackson) | Generic tree, path-scoped update | `./run_json_test.sh` | **PASS** | Same mechanism as Ion generic mode, via Jackson's `ObjectNode`. |
| JSON (Jackson) | Bind to narrow typed record | `./run_json_test.sh --typed` | **FAIL** | Same mechanism as Ion typed mode. Jackson's default setting (`FAIL_ON_UNKNOWN_PROPERTIES=true`) would reject this binding outright; the test disables that setting to reach the silent-loss outcome. |
| JSON (Jackson) | Bind to typed DTO with catch-all property handler | `./run_json_test.sh --overflow` | **PASS** | DTO declares explicit fields for `name`/`age` and a `@JsonAnySetter`/`@JsonAnyGetter` pair that captures and re-emits any additional property, including ones added after the DTO was written. |
| CBOR (Jackson) | Generic tree, path-scoped update | `./run_cbor_test.sh` | **PASS** | Identical mechanism to JSON generic mode. CBOR and JSON share Jackson's tree model; only the on-disk byte encoding differs. |
| CBOR (Jackson) | Bind to narrow typed record | `./run_cbor_test.sh --typed` | **FAIL** | Identical mechanism to JSON typed mode. |
| CBOR (Jackson) | Bind to typed DTO with catch-all property handler | `./run_cbor_test.sh --overflow` | **PASS** | Identical mechanism to JSON overflow mode. |

### 2.1 Root cause, precisely stated

A field survives the round-trip if and only if every step between
reading and re-writing the document retains a place to store it.

- **Protobuf** guarantees this at the library level: the generated
  `Builder` always retains an `UnknownFieldSet` regardless of which
  fields the application code reads or writes, unless that set is
  explicitly cleared.
- **Ion, JSON, and CBOR** provide no such guarantee at the library
  level. Preservation is a direct consequence of which Java
  representation the reader's code chooses:
  - A generic tree representation (`IonStruct`, `ObjectNode`)
    retains every key present in the source document, because it was
    never narrowed to a fixed set of fields.
  - A narrow typed class (a record or DTO exposing only the fields
    that class declares) has no slot to hold a field it doesn't
    define. Reconstructing output from that typed instance discards
    anything outside its declared shape.
  - A typed class with an explicit catch-all handler
    (`@JsonAnySetter`/`@JsonAnyGetter` in Jackson) combines both
    properties: declared fields are still strongly typed, and
    undeclared fields are captured into a generic bucket and
    re-emitted on write.

## 3. Implementation Detail

### 3.1 Protocol Buffers

**Files:**
```
proto/v1/person.proto                 # old schema: name, age
proto/v2/person.proto                 # new schema: name, age, email (new field, new tag)
generate-proto.sh                     # runs local protoc -> src/main/java
src/main/java/.../Writer.java         # v2: writes name, age, email
src/main/java/.../Reader.java         # v1: reads the file, updates age, writes it back
src/main/java/.../Verifier.java       # v2: re-reads the file, asserts age and email
run_test.sh                           # orchestrates the full scenario
```

`v1` and `v2` are compiled into separate Java packages
(`com.waltest.protoevolution.v1`, `...v2`) from two separate
`.proto` files. This mirrors production reality: the "old reader"
and "new writer" are two independently deployed services, each built
from whichever schema version was current at their respective build
time. Compiling both into one module keeps the test self-contained
without requiring two separate deployable services.

**Commands:**
```bash
./run_test.sh              # Reader preserves unknown fields by default -> PASS
./run_test.sh --discard    # Reader explicitly clears UnknownFieldSet -> FAIL (negative control)
```

**Schema evolution rule this encodes:**
- Adding a field with a new, previously unused tag number is a safe,
  backward-compatible change.
- Reusing a tag number, or changing the wire type of an existing
  tag, is a breaking change — not covered by this guarantee.
- This guarantee applies specifically to the **binary wire format**
  (`writeTo`/`parseFrom`). Protobuf's JSON output
  (`JsonFormat.printer()`) does not retain unknown fields by
  default; it is not exercised by this test.

**Requirements:** JDK 21+, Maven, a local `protoc` binary on `PATH`.
`pom.xml` pins `protobuf-java` to match the local `protoc` version
(currently 31.1 / 4.31.1); keep these in sync if either is upgraded.

### 3.2 Amazon Ion

**Files:**
```
src/main/java/.../ion/Writer.java     # writes name, age, email as an IonStruct
src/main/java/.../ion/Reader.java     # two modes: generic struct update, or typed record binding
src/main/java/.../ion/Verifier.java   # re-reads and asserts age and email
run_ion_test.sh                       # orchestrates the scenario
```

Ion is a self-describing format: there is no compiled schema class
and no wire-format-level unknown-field retention. `Reader.java`
implements both representative strategies as explicit modes:

| Mode | Reader behavior |
|---|---|
| default | Loads the document as a generic `IonStruct`. Mutates the `age` entry in place via `struct.put(...)`. Writes the entire struct back out. |
| `--typed` | Reads `name` and `age` into a Java record `PersonV1(String name, int age)`. Constructs a brand-new `IonStruct` containing only those two fields and writes that. |

**Commands:**
```bash
./run_ion_test.sh            # generic struct mode -> PASS
./run_ion_test.sh --typed    # typed record mode -> FAIL
```

### 3.3 JSON and CBOR (Jackson)

**Files:**
```
src/main/java/.../jackson/ScenarioWriter.java    # shared: writes name, age, email
src/main/java/.../jackson/ScenarioReader.java    # shared: three modes (see below)
src/main/java/.../jackson/ScenarioVerifier.java  # shared: asserts age and email
src/main/java/.../jackson/PersonV1Safe.java      # typed DTO with catch-all property handler
src/main/java/.../json/{Writer,Reader,Verifier}.java   # JSON entry points (plain ObjectMapper)
src/main/java/.../cbor/{Writer,Reader,Verifier}.java   # CBOR entry points (CBORMapper)
run_json_test.sh
run_cbor_test.sh
```

JSON and CBOR share one implementation (`jackson/Scenario*.java`).
Jackson's tree model (`JsonNode`/`ObjectNode`) is identical
regardless of which format produced it; `JsonMapper` and `CBORMapper`
differ only in how they encode/decode bytes on disk. The
format-specific `json`/`cbor` packages are thin entry points that
select the concrete `ObjectMapper` subtype and delegate to the
shared logic.

`ScenarioReader` implements three modes, selected via
`ScenarioReader.Mode`:

| Mode | CLI flag | Reader behavior |
|---|---|---|
| `GENERIC` | *(none)* | Loads the document as a generic `ObjectNode`. Mutates the `age` entry in place. Writes the whole node back out. |
| `TYPED_LOSSY` | `--typed` | Binds the document into `PersonV1(String name, int age)` via `treeToValue(...)`. Constructs a new `ObjectNode` from only those two fields and writes that. Uses a mapper with `FAIL_ON_UNKNOWN_PROPERTIES` disabled — Jackson's default setting would otherwise reject this binding with an exception rather than silently dropping the field. |
| `TYPED_OVERFLOW` | `--overflow` | Binds the document into `PersonV1Safe`, a class exposing typed `name`/`age` accessors plus a `@JsonAnySetter`-annotated method that captures any other property into an internal map, and a matching `@JsonAnyGetter` that re-emits that map's contents on serialize. No mapper reconfiguration is required; the any-setter itself prevents an "unrecognized property" condition from occurring. |

**Commands:**
```bash
./run_json_test.sh              # GENERIC        -> PASS
./run_json_test.sh --typed      # TYPED_LOSSY     -> FAIL
./run_json_test.sh --overflow   # TYPED_OVERFLOW  -> PASS

./run_cbor_test.sh              # GENERIC        -> PASS
./run_cbor_test.sh --typed      # TYPED_LOSSY     -> FAIL
./run_cbor_test.sh --overflow   # TYPED_OVERFLOW  -> PASS
```

## 4. Requirements

- JDK 21+
- Apache Maven
- Local `protoc` binary on `PATH` (Protobuf test only)
- Network access to the configured Maven repository for dependency
  resolution (`protobuf-java`, `ion-java`, `jackson-databind`,
  `jackson-dataformat-cbor`)
