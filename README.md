# Schema Evolution Compatibility Tests

Automated tests validating forward/backward-compatible schema
evolution across six serialization formats: Protocol Buffers, Amazon
Ion, JSON, CBOR, Apache Avro, and Apache Thrift.

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

14 total test runs across 6 formats. `./run_all.sh` executes all of
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
| Avro | Read via embedded writer schema (no resolution) | `./run_avro_test.sh` | **PASS** | Avro container files embed the full writer schema in the file header. Reading with that schema directly (not resolving to a narrower one) returns a `GenericRecord` with every original field. |
| Avro | Resolve to narrow reader schema | `./run_avro_test.sh --typed` | **FAIL** | Avro's schema-resolution logic skips any writer field absent from the supplied reader schema **while parsing**. The field is never read into memory, not merely omitted on write. |
| Thrift | Default (generated struct) | `./run_thrift_test.sh` | **FAIL** | Standard generated Thrift code has no unknown-field retention mechanism. An unrecognized field ID is read past and discarded (`TProtocolUtil.skip(...)`) with no option to retain it. This is the ordinary, unmodified behavior — no special flag is required to produce this result. |
| Thrift | Hand-rolled generic struct | `./run_thrift_test.sh --manual-preserve` | **PASS** | A custom reader (`GenericThriftStruct`, not part of any Thrift library) parses every field by its wire-level ID into a map, independent of any compiled struct definition, and re-emits all of them on write. |

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
- **Avro** requires a schema to decode at all — its binary encoding
  carries no field names or tags. Whether an old reader retains a new
  field depends on which schema it hands to the decoder: the file's
  own embedded writer schema (no resolution, retains everything), or
  its own narrower compiled schema (resolution, which skips and
  discards unrecognized writer fields at parse time, before
  application code runs at all).
- **Thrift**'s wire format (`TBinaryProtocol`) is tag-based, the same
  conceptual mechanism as protobuf's. Unlike protobuf, however, the
  code the `thrift` compiler generates implements no retention step
  for unrecognized tags — it only skips them. The wire format could
  support preservation; the generated code simply does not implement
  it. Achieving it requires code equivalent to `GenericThriftStruct`
  in this repository, which is not provided by Thrift itself.

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

### 3.4 Apache Avro

**Files:**
```
src/main/resources/avro/v1/person.avsc        # old schema: name, age
src/main/resources/avro/v2/person.avsc        # new schema: name, age, email
src/main/java/.../avro/AvroSchemas.java       # loads the .avsc resources
src/main/java/.../avro/Writer.java            # writes an Avro Object Container File
src/main/java/.../avro/Reader.java            # two modes: embedded-schema read, or resolve to v1
src/main/java/.../avro/Verifier.java          # re-reads and asserts age and email
run_avro_test.sh
```

Avro's binary encoding carries no field names or tags; a schema is
required to decode it at all. Avro Object Container Files address
this by embedding the full writer schema in the file's own header.
`Reader.java` implements two modes that differ only in which schema
is handed to the decoder:

| Mode | CLI flag | Reader behavior |
|---|---|---|
| default | *(none)* | Opens the file with `GenericDatumReader<>()` and no explicit reader schema. The embedded writer schema (v2) is used as-is, returning a `GenericRecord` with every original field. Mutates `age`, writes the full record back with that same schema. |
| `--typed` | `--typed` | Opens the file with `GenericDatumReader<>()` whose expected schema is explicitly set to v1 via `setExpected(...)`. Avro's schema-resolution logic skips the `email` field while parsing, because it is absent from the reader schema. Writes the resolved record back using the v1 schema only. |

**Commands:**
```bash
./run_avro_test.sh            # embedded schema, no resolution -> PASS
./run_avro_test.sh --typed    # resolved to v1 schema -> FAIL
```

### 3.5 Apache Thrift

**Files:**
```
src/main/java/.../thrift/PersonV1.java              # hand-written, matches thrift-codegen shape for v1 IDL
src/main/java/.../thrift/PersonV2.java              # hand-written, matches thrift-codegen shape for v2 IDL
src/main/java/.../thrift/GenericThriftStruct.java   # hand-rolled generic reader/writer (not a Thrift feature)
src/main/java/.../thrift/Writer.java                # writes name, age, email via TBinaryProtocol
src/main/java/.../thrift/Reader.java                # two modes: generated struct, or hand-rolled generic struct
src/main/java/.../thrift/Verifier.java              # re-reads and asserts age and email
run_thrift_test.sh
```

No `thrift` compiler is used; `PersonV1` and `PersonV2` are written
by hand to match the exact `read()`/`write()` structure the `thrift`
compiler produces for the equivalent IDL. This keeps the
unknown-field-handling logic (the `default:` branch in `read()`)
visible in source rather than hidden inside generated output.

| Mode | CLI flag | Reader behavior |
|---|---|---|
| default | *(none)* | Reads the file into a `PersonV1` instance via ordinary generated-style `read()`. Field ID 3 (`email`) has no `case` in that method's `switch` statement, so `TProtocolUtil.skip(...)` consumes and discards its bytes during parsing. Mutates `age`, writes a `PersonV1` (two fields only) back out. |
| `--manual-preserve` | `--manual-preserve` | Reads the file with `GenericThriftStruct.readFrom(...)`, which loops over every field by its wire-level ID into a map, independent of any compiled struct. Mutates the value at ID 2, re-emits every captured ID/value pair on write. |

**Commands:**
```bash
./run_thrift_test.sh                      # generated struct, default behavior -> FAIL
./run_thrift_test.sh --manual-preserve    # hand-rolled generic struct -> PASS
```

Note the default-behavior polarity is reversed relative to every
other format in this suite: elsewhere, the safe outcome is the
default and an explicit flag is required to break it. For Thrift, the
lossy outcome is the default, and an explicit, non-standard
implementation is required to avoid it.

## 4. Requirements

- JDK 21+
- Apache Maven
- Local `protoc` binary on `PATH` (Protobuf test only)
- Network access to the configured Maven repository for dependency
  resolution (`protobuf-java`, `ion-java`, `jackson-databind`,
  `jackson-dataformat-cbor`, `avro`, `libthrift`)
