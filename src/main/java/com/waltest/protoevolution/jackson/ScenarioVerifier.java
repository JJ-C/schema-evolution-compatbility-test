package com.waltest.protoevolution.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Format-agnostic verification logic, shared by the JSON and CBOR
 * variants.
 */
public final class ScenarioVerifier {

    private ScenarioVerifier() {
    }

    public static void verify(ObjectMapper mapper, Path dataFile, int expectedAge, String expectedEmail,
                               String label) throws IOException {
        JsonNode reread = mapper.readTree(dataFile.toFile());
        System.out.println("[" + label + " Writer] Re-read after v1 round-trip: " + reread);

        int actualAge = reread.path("age").asInt(-1);
        String actualEmail = reread.path("email").asText("");

        boolean ageOk = actualAge == expectedAge;
        boolean emailOk = expectedEmail.equals(actualEmail);

        System.out.printf("  age   expected=%d actual=%d -> %s%n", expectedAge, actualAge, ageOk ? "PASS" : "FAIL");
        System.out.printf("  email expected=%s actual=%s -> %s%n",
                expectedEmail, actualEmail, emailOk ? "PASS" : "FAIL");

        if (ageOk && emailOk) {
            System.out.println("RESULT: PASS - new field survived old-schema round-trip, existing field updated");
        } else {
            System.out.println("RESULT: FAIL - schema evolution contract broken");
            System.exit(1);
        }
    }
}
