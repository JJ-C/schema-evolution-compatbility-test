package com.waltest.protoevolution.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the service running the NEW (v2) schema.
 *
 * Writes an Avro Object Container File, which embeds the FULL writer
 * schema (v2, including "email") in the file's own header metadata.
 * That embedded schema is what makes Avro's "generic, no resolution"
 * reading mode possible later -- an old reader can recover the exact
 * schema the file was written with, without needing its own copy.
 */
public final class Writer {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.avro");
        Files.createDirectories(dataFile.getParent());

        Schema v2 = AvroSchemas.v2();
        GenericRecord person = new GenericData.Record(v2);
        person.put("name", "Alice");
        person.put("age", 30);
        person.put("email", "alice@example.com");

        try (DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(new GenericDatumWriter<>(v2))) {
            fileWriter.create(v2, dataFile.toFile());
            fileWriter.append(person);
        }

        System.out.println("[Avro Writer] Wrote: " + person);
        System.out.println("[Avro Writer] -> " + dataFile.toAbsolutePath());
    }
}
