package com.waltest.protoevolution;

import com.waltest.protoevolution.v2.PersonProto.Person;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the v2 Writer re-reading the file after the v1 Reader
 * touched it, and asserting the schema-evolution contract held:
 *   1. "age" reflects the v1 Reader's update.
 *   2. "email" (added in v2, invisible to v1) is still intact.
 */
public final class Verifier {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.bin");
        int expectedAge = Integer.parseInt(args.length > 1 ? args[1] : "31");
        String expectedEmail = args.length > 2 ? args[2] : "alice@example.com";

        byte[] bytes = Files.readAllBytes(dataFile);
        Person reread = Person.parseFrom(bytes);

        System.out.println("[Writer v2] Re-read after v1 round-trip: " + reread);

        boolean ageOk = reread.getAge() == expectedAge;
        boolean emailOk = expectedEmail.equals(reread.getEmail());

        System.out.printf("  age   expected=%d actual=%d -> %s%n",
                expectedAge, reread.getAge(), ageOk ? "PASS" : "FAIL");
        System.out.printf("  email expected=%s actual=%s -> %s%n",
                expectedEmail, reread.getEmail(), emailOk ? "PASS" : "FAIL");

        if (ageOk && emailOk) {
            System.out.println("RESULT: PASS - new field survived old-schema round-trip, existing field updated");
        } else {
            System.out.println("RESULT: FAIL - schema evolution contract broken");
            System.exit(1);
        }
    }
}
