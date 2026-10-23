package com.waltest.protoevolution.ion;

import com.amazon.ion.IonInt;
import com.amazon.ion.IonLoader;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simulates the v2 Writer re-reading the file after the v1 Reader
 * touched it, and asserting the schema-evolution contract held.
 *
 * Unlike the protobuf Verifier, this one can't rely on generated
 * code to "just know" email's tag number -- it reads the field by
 * name off the generic struct, same as the Writer did.
 */
public final class Verifier {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.ion");
        int expectedAge = Integer.parseInt(args.length > 1 ? args[1] : "31");
        String expectedEmail = args.length > 2 ? args[2] : "alice@example.com";

        IonSystem ionSystem = IonSystemBuilder.standard().build();
        IonLoader loader = ionSystem.getLoader();
        IonStruct reread = (IonStruct) loader.load(dataFile.toFile()).get(0);

        System.out.println("[Writer] Re-read after v1 round-trip: " + reread);

        int actualAge = ((IonInt) reread.get("age")).intValue();
        IonString emailValue = (IonString) reread.get("email");
        String actualEmail = emailValue == null ? "" : emailValue.stringValue();

        boolean ageOk = actualAge == expectedAge;
        boolean emailOk = expectedEmail.equals(actualEmail);

        System.out.printf("  age   expected=%d actual=%d -> %s%n",
                expectedAge, actualAge, ageOk ? "PASS" : "FAIL");
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
