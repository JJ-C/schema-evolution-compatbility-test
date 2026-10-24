# proto-test — Schema Evolution Round-Trip Tests (Protobuf, Ion, JSON, CBOR)

Tests the classic protobuf forward/backward-compatibility guarantee:

> A writer on a **newer** schema adds a field. A reader still on the
> **older** schema reads the message, updates an *existing* field, and
> writes it back out. When the writer reads that file again, the field
> it added should still be there, untouched — even though the old
> reader never knew it existed.

This works because protobuf's binary wire format stores fields by tag
number, not by name/position. A parser that doesn't recognize a tag
keeps its raw bytes as an "unknown field" and re-emits them verbatim
on the next serialize — *as long as nobody explicitly discards them*.

## Layout

```
proto/v1/person.proto   # old schema: name, age
proto/v2/person.proto   # new schema: name, age, email (new field, new tag)
generate-proto.sh       # runs local protoc -> src/main/java
src/main/java/.../
  Writer.java    # v2: writes name/age/email
  Reader.java    # v1: reads it (email is invisible), bumps age, writes back
  Verifier.java  # v2: re-reads, asserts age updated AND email preserved
run_test.sh      # orchestrates the whole scenario end-to-end
```

Why two separate generated packages (`v1` and `v2`) instead of one
proto file? Because in real life the "old reader" and "new writer"
are two different deployed services built from two different versions
of the `.proto` file at two different points in time. Compiling both
versions side-by-side (into different Java packages) is the most
honest way to simulate that without needing two separate services.

## Running it

```bash
./run_test.sh              # happy path: email survives, age updates -> PASS
./run_test.sh --discard    # negative control: reader drops unknown fields -> FAIL
```

`--discard` isn't part of the real scenario — it exists only to prove
the test harness actually catches a broken implementation instead of
rubber-stamping PASS no matter what.

## The schema evolution rule this encodes

-  Adding a new field with a **new, never-before-used tag number** is
  safe. Old readers ignore it; the bytes just ride along.
-  Reusing a tag number, changing a tag's wire type, or changing a
  required/singular field's type is **not** safe — that's actual
  schema-breaking change, not evolution.
-  This guarantee is specific to **binary wire format**
  (`writeTo`/`parseFrom`). If you serialize to **JSON**
  (`JsonFormat.printer()`), unknown fields are dropped by default —
  JSON round-trips do not get this safety net for free.

## Requirements

- JDK 21+, Maven, and a local `protoc` binary on `PATH`.
- `pom.xml` pins `protobuf-java` to match your local `protoc` version
  (currently 31.1 / 4.31.1) — keep these in sync if you upgrade either.

---

## Same scenario with Amazon Ion

`src/main/java/.../ion/` repeats the exact same experiment, but with
Amazon Ion instead of protobuf — and the result is genuinely
different, which is the whole point of running it.

Ion is **self-describing** (like JSON): there's no compiled schema
class, no wire-format tag numbers, no generated Builder that
automatically drags unknown fields along for you. Whether `email`
survives a round-trip through the old reader depends entirely on
*how that reader's code is written* — there is no free safety net.

Run both modes:

```bash
./run_ion_test.sh            # correct approach -> PASS
./run_ion_test.sh --typed    # common real-world bug -> FAIL
```

| Approach | What it does | Result |
|---|---|---|
| `ion/Reader` default (generic struct) | Loads the whole document as a generic `IonStruct`, mutates only the `age` field in place, writes the *entire* struct back out. Never reconstructs the document from a narrow shape. | `email` **survives** |
| `ion/Reader --typed` | Deserializes into a narrow `PersonV1(name, age)` record (the Ion equivalent of `objectMapper.readValue(json, V1.class)`), then rebuilds the output document FROM that record. | `email` is **silently dropped** |

### The lesson this test teaches

Protobuf and Ion both *can* carry forward-compatible data through an
old reader — but they put the compatibility burden in different
places:

- **Protobuf**: compatibility is enforced by the **wire format
  itself**. Generated code preserves unknown fields no matter how you
  use it, as long as you don't go out of your way to discard them
  (see `--discard` above).
- **Ion / JSON-like formats**: compatibility is a **property of your
  code**, not the format. If you deserialize into a narrow typed
  shape and reserialize from that shape, you've effectively defined
  your own mini schema that silently drops anything outside it — even
  though the underlying format had no problem carrying it. The fix is
  architectural: do partial, path-scoped updates on the generic
  document instead of full deserialize/reserialize round-trips through
  a narrow type.

If a service in your real system binds Ion/JSON payloads straight
into DTOs and writes those DTOs back out, it has this bug by default —
whether or not anyone's tested for it. `--typed` mode above is that
bug, caught in a bottle.

---

## Same scenario with plain JSON and CBOR (Jackson)

`src/main/java/.../jackson/` holds format-agnostic scenario logic
shared by two thin variants:

- `src/main/java/.../json/` — plain text JSON via a stock `ObjectMapper`
- `src/main/java/.../cbor/` — binary CBOR via Jackson's `CBORMapper`

They're the same three classes (`Writer`/`Reader`/`Verifier`) and the
same logic either way, because Jackson's tree model (`JsonNode`/
`ObjectNode`) doesn't care which format produced it — only the bytes
on disk differ. That's actually the point of including both: it shows
the compatibility story has nothing to do with "binary vs text" and
everything to do with "generic tree vs typed DTO."

```bash
./run_json_test.sh              # generic tree -> PASS
./run_json_test.sh --typed      # narrow DTO round-trip, the common real bug -> FAIL
./run_json_test.sh --overflow   # narrow DTO + @JsonAnySetter/@JsonAnyGetter -> PASS

./run_cbor_test.sh              # generic tree -> PASS
./run_cbor_test.sh --typed      # narrow DTO round-trip, the common real bug -> FAIL
./run_cbor_test.sh --overflow   # narrow DTO + @JsonAnySetter/@JsonAnyGetter -> PASS
```

| Approach | What it does | Result |
|---|---|---|
| `Reader` default (generic tree) | Loads the document as a generic `ObjectNode`, mutates only `age` in place, writes the whole node back out. | `email` **survives** |
| `Reader --typed` | Binds into a narrow `PersonV1(name, age)` record via `treeToValue(...)`, then rebuilds the output document FROM that record. | `email` is **silently dropped** |
| `Reader --overflow` | Binds into `PersonV1Safe`, a typed DTO with explicit `name`/`age` plus `@JsonAnySetter`/`@JsonAnyGetter` catching anything else into an overflow map. | `email` **survives**, and you get real typed access to the known fields too |

One extra wrinkle worth knowing: Jackson's **default** setting
(`FAIL_ON_UNKNOWN_PROPERTIES = true`) would actually **throw an
exception** the moment you tried to bind JSON/CBOR containing `email`
into a `PersonV1` record that doesn't declare it — fail loud, not
fail silent. The `--typed` mode here deliberately disables that
feature to simulate the extremely common real-world move of "just set
ignore-unknown-properties so deserialization stops crashing," which is
exactly the change that turns a loud, obvious bug into a silent,
invisible one. Arguably worse than not catching it at all.

Notice `--overflow` never needed to touch `FAIL_ON_UNKNOWN_PROPERTIES`
at all — `@JsonAnySetter` *is* the catch-all handler, so there's no
"unrecognized property" to fail on in the first place. It gives you
both type safety for the fields you know about AND forward-compat for
the ones you don't. The catch: almost nobody writes this by default.
It's a deliberate opt-in most teams only add after `--typed`'s bug
has already bitten them once in production.

## Full comparison across all four formats

| Format | Preservation guaranteed by | Breaks by default? | How to break it on purpose | Typed + safe option? |
|---|---|---|---|---|
| Protobuf | the wire format + generated code | No — safe by default | `--discard` (manually strip `UnknownFieldSet`) | Yes, always (it's the default) |
| Amazon Ion | nothing — 100% up to your code | No, but only if you stay generic | `--typed` (bind into a narrow record) | Not demoed here, but the same generic-struct pattern applies |
| JSON (Jackson) | nothing — 100% up to your code | No, but only if you stay generic | `--typed` (same, plus Jackson will warn you by crashing unless you silence it) | Yes — `--overflow` (`@JsonAnySetter`/`@JsonAnyGetter`) |
| CBOR (Jackson) | nothing — 100% up to your code | No, but only if you stay generic | `--typed` (identical to JSON — CBOR is just a binary encoding of the same tree model) | Yes — `--overflow`, identical mechanism to JSON |
