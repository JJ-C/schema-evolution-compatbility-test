package com.waltest.protoevolution;

import com.google.protobuf.ExtensionRegistryLite;
import com.waltest.protoevolution.v1.PersonProto.Person;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the service still running the OLD (v1) schema.
 * It has never heard of "email" -- field 3 is just unknown bytes to it.
 *
 * It reads the file written by the v2 Writer, updates an EXISTING
 * field (age), and writes the file back out.
 *
 * By default this preserves unknown fields (the real-world, correct
 * behavior of protobuf-java). Pass "--discard" as a second CLI arg to
 * see the broken alternative, where email would be lost forever.
 */
public final class Reader {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.bin");
        boolean discardUnknown = args.length > 1 && "--discard".equals(args[1]);

        byte[] bytes = Files.readAllBytes(dataFile);

        Person person;
        if (discardUnknown) {
            // Demonstrates the WRONG way to do this: an extension
            // registry / parser configured to drop unknown fields
            // would silently throw away "email" here.
            person = Person.parser()
                    .parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry());
            person = person.toBuilder()
                    .setUnknownFields(com.google.protobuf.UnknownFieldSet.getDefaultInstance())
                    .build();
            System.out.println("[Reader v1] !! running in --discard mode, unknown fields dropped !!");
        } else {
            person = Person.parseFrom(bytes);
        }

        System.out.println("[Reader v1] Read (v1 view, no email field): " + person);

        // Update an EXISTING field only. The v1 schema has no idea
        // "email" exists, so it can't touch it -- it just carries
        // along whatever unknown bytes were already there.
        Person updated = person.toBuilder()
                .setAge(person.getAge() + 1)
                .build();

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile())) {
            updated.writeTo(out);
        }

        System.out.println("[Reader v1] Wrote back with age bumped: " + updated);
    }
}
