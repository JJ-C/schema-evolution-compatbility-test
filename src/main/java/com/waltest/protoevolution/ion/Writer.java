package com.waltest.protoevolution.ion;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the service running the NEW schema.
 *
 * Unlike protobuf, Ion has no compiled "v2 schema class" -- it's
 * self-describing. "New schema" here just means "the writer happens
 * to include an email field." There is no generated code to pin this
 * down; it's purely a convention both sides have to honor.
 */
public final class Writer {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.ion");
        Files.createDirectories(dataFile.getParent());

        IonSystem ionSystem = IonSystemBuilder.standard().build();
        IonStruct person = ionSystem.newEmptyStruct();
        person.add("name", ionSystem.newString("Alice"));
        person.add("age", ionSystem.newInt(30));
        person.add("email", ionSystem.newString("alice@example.com"));

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile());
             IonWriter writer = IonTextWriterBuilder.pretty().build(out)) {
            person.writeTo(writer);
        }

        System.out.println("[Writer] Wrote: " + person);
        System.out.println("[Writer] -> " + dataFile.toAbsolutePath());
    }
}
