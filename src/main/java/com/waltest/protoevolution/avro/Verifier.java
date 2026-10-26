package com.waltest.protoevolution.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simulates the v2 Writer re-reading the file after the v1 Reader
 * touched it, and asserting the schema-evolution contract held.
 */
public final class Verifier {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.avro");
        int expectedAge = Integer.parseInt(args.length > 1 ? args[1] : "31");
        String expectedEmail = args.length > 2 ? args[2] : "alice@example.com";

        GenericRecord reread;
        try (DataFileReader<GenericRecord> fileReader =
                     new DataFileReader<>(dataFile.toFile(), new GenericDatumReader<>())) {
            reread = fileReader.next();
        }

        System.out.println("[Avro Writer] Re-read after v1 round-trip: " + reread);

        int actualAge = (Integer) reread.get("age");
        Schema.Field emailField = reread.getSchema().getField("email");
        String actualEmail = emailField == null ? "" : String.valueOf(reread.get("email"));

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
