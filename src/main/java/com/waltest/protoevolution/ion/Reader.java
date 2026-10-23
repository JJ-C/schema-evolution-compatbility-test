package com.waltest.protoevolution.ion;

import com.amazon.ion.IonInt;
import com.amazon.ion.IonLoader;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Simulates the service still running the OLD schema -- one that was
 * written before "email" existed and only knows about name/age.
 *
 * Ion has NO automatic unknown-field preservation the way protobuf's
 * wire format does. Whether "email" survives depends entirely on HOW
 * the old reader's code is written:
 *
 *   --generic (default, CORRECT): load the document as a generic
 *     IonStruct, mutate only the "age" path in place, write the whole
 *     struct back. Never touches/drops anything it didn't look at.
 *
 *   --typed (BROKEN, the common real-world bug): deserialize into a
 *     narrow v1 POJO/record that only has name+age (think Jackson's
 *     ObjectMapper.readValue(json, V1Person.class)), then re-serialize
 *     FROM that POJO. Email was never captured by the POJO, so it's
 *     gone the moment you write the POJO back out -- silently.
 */
public final class Reader {

    /** The "v1 schema" as a narrow, compiled-in Java shape. */
    private record PersonV1(String name, int age) {
    }

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.ion");
        boolean typedMode = args.length > 1 && "--typed".equals(args[1]);

        IonSystem ionSystem = IonSystemBuilder.standard().build();
        IonLoader loader = ionSystem.getLoader();
        IonStruct person = (IonStruct) loader.load(dataFile.toFile()).get(0);

        System.out.println("[Reader v1] Read: " + person);
        explainUnknownFields(person);

        if (typedMode) {
            System.out.println("[Reader v1] !! running in --typed mode: binding into a narrow"
                    + " name/age-only record, which drops anything else !!");
            PersonV1 bound = new PersonV1(
                    ((IonString) person.get("name")).stringValue(),
                    ((IonInt) person.get("age")).intValue());
            PersonV1 updated = new PersonV1(bound.name(), bound.age() + 1);

            // Rebuilding the document FROM the narrow record: "email"
            // was never part of PersonV1, so it cannot come back.
            IonStruct rebuilt = ionSystem.newEmptyStruct();
            rebuilt.add("name", ionSystem.newString(updated.name()));
            rebuilt.add("age", ionSystem.newInt(updated.age()));
            writeOut(dataFile, rebuilt);
            System.out.println("[Reader v1] Wrote back (typed, lossy): " + rebuilt);
        } else {
            // Mutate only the field we actually mean to change. Every
            // other path in the struct -- known or not -- rides along
            // untouched because we never reconstructed the document.
            int currentAge = ((IonInt) person.get("age")).intValue();
            person.put("age", ionSystem.newInt(currentAge + 1));
            writeOut(dataFile, person);
            System.out.println("[Reader v1] Wrote back (generic, preserves unknowns): " + person);
        }
    }

    private static void writeOut(Path dataFile, IonStruct struct) throws IOException {
        try (FileOutputStream out = new FileOutputStream(dataFile.toFile());
             IonWriter writer = IonTextWriterBuilder.pretty().build(out)) {
            struct.writeTo(writer);
        }
    }

    private static void explainUnknownFields(IonStruct person) {
        for (var value : person) {
            String field = value.getFieldName();
            if (!field.equals("name") && !field.equals("age")) {
                System.out.println("[Reader v1] NOTE: field '" + field
                        + "' is unknown to v1's compiled shape, but Ion still exposes it by"
                        + " name on the generic struct. Whether it survives depends on how"
                        + " this code chooses to read/write the document.");
            }
        }
    }
}
