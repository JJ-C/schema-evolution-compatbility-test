package com.waltest.protoevolution.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Format-agnostic reader logic, shared by the JSON and CBOR variants.
 *
 * Same story as the Ion test: there's no wire-format-level unknown
 * field preservation here. It's purely a function of whether the code
 * does a generic, path-scoped update (safe) or a full typed
 * deserialize/reserialize round-trip (lossy).
 */
public final class ScenarioReader {

    private ScenarioReader() {
    }

    /** The "v1 schema" as a narrow, compiled-in Java shape. */
    public record PersonV1(String name, int age) {
    }

    public static void read(ObjectMapper mapper, Supplier<ObjectMapper> lenientMapperSupplier,
                             Path dataFile, boolean typedMode, String label) throws IOException {
        JsonNode root = mapper.readTree(dataFile.toFile());
        System.out.println("[" + label + " Reader v1] Read: " + root);
        explainUnknownFields(root, label);

        if (typedMode) {
            runTypedLossyPath(mapper, lenientMapperSupplier, dataFile, root, label);
        } else {
            runGenericSafePath(mapper, dataFile, root, label);
        }
    }

    private static void runTypedLossyPath(ObjectMapper mapper, Supplier<ObjectMapper> lenientMapperSupplier,
                                           Path dataFile, JsonNode root, String label) throws IOException {
        System.out.println("[" + label + " Reader v1] !! --typed mode: binding into a narrow name/age-only"
                + " record. Jackson's DEFAULT setting would THROW on the unknown 'email' property; this"
                + " demo disables FAIL_ON_UNKNOWN_PROPERTIES (a very common 'just be lenient' tweak teams"
                + " add to stop crashes), which quietly drops the field instead of failing loud !!");

        // The lenient (ignore-unknown-properties) mapper has to be built
        // by the caller, which knows its own concrete mapper type
        // (JsonMapper/CBORMapper) and therefore has access to the
        // Jackson-3-friendly immutable .builder()...build() API. This
        // shared method only ever consumes mappers, never reconfigures
        // one via the deprecated mutable .disable(...) in place.
        ObjectMapper lenient = lenientMapperSupplier.get();
        PersonV1 bound = lenient.treeToValue(root, PersonV1.class);
        PersonV1 updated = new PersonV1(bound.name(), bound.age() + 1);

        // Rebuilding the document FROM the narrow record: "email" was
        // never part of PersonV1, so it cannot come back.
        ObjectNode rebuilt = mapper.createObjectNode();
        rebuilt.put("name", updated.name());
        rebuilt.put("age", updated.age());
        mapper.writeValue(dataFile.toFile(), rebuilt);

        System.out.println("[" + label + " Reader v1] Wrote back (typed, lossy): " + rebuilt);
    }

    private static void runGenericSafePath(ObjectMapper mapper, Path dataFile, JsonNode root, String label)
            throws IOException {
        // Mutate only the field we actually mean to change. Every
        // other field -- known or not -- rides along untouched
        // because we never reconstructed the document from a type.
        ObjectNode struct = (ObjectNode) root;
        int currentAge = struct.get("age").asInt();
        struct.put("age", currentAge + 1);
        mapper.writeValue(dataFile.toFile(), struct);

        System.out.println("[" + label + " Reader v1] Wrote back (generic, preserves unknowns): " + struct);
    }

    private static void explainUnknownFields(JsonNode root, String label) {
        root.fieldNames().forEachRemaining(field -> {
            if (!field.equals("name") && !field.equals("age")) {
                System.out.println("[" + label + " Reader v1] NOTE: field '" + field
                        + "' is unknown to v1's compiled shape, but the generic tree still exposes it"
                        + " by name. Whether it survives depends on how this code reads/writes the"
                        + " document.");
            }
        });
    }
}
