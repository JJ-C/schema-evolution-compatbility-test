package com.waltest.protoevolution.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Format-agnostic writer logic, shared by the JSON and CBOR variants.
 * Jackson's tree model (ObjectNode/JsonNode) is identical regardless
 * of which ObjectMapper (plain or CBORMapper) produced it -- only the
 * bytes on disk differ.
 */
public final class ScenarioWriter {

    private ScenarioWriter() {
    }

    public static void write(ObjectMapper mapper, Path dataFile, String label) throws IOException {
        Files.createDirectories(dataFile.getParent());

        ObjectNode person = mapper.createObjectNode();
        person.put("name", "Alice");
        person.put("age", 30);
        person.put("email", "alice@example.com");

        mapper.writeValue(dataFile.toFile(), person);

        System.out.println("[" + label + " Writer] Wrote: " + person);
        System.out.println("[" + label + " Writer] -> " + dataFile.toAbsolutePath());
    }
}
