package com.waltest.protoevolution;

import com.waltest.protoevolution.v2.PersonProto.Person;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the service running the NEW (v2) schema.
 * Writes a Person that includes the "email" field which does not
 * exist in the v1 schema yet.
 */
public final class Writer {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.bin");
        Files.createDirectories(dataFile.getParent());

        Person person = Person.newBuilder()
                .setName("Alice")
                .setAge(30)
                .setEmail("alice@example.com")
                .build();

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile())) {
            person.writeTo(out);
        }

        System.out.println("[Writer v2] Wrote: " + person);
        System.out.println("[Writer v2] -> " + dataFile.toAbsolutePath());
    }
}
