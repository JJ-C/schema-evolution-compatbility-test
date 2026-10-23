# proto-test — Proto Schema Evolution Round-Trip Test

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
