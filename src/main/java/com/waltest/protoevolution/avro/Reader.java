package com.waltest.protoevolution.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Simulates the service still running the OLD (v1) schema.
 *
 * Avro's binary encoding is purely positional -- there is no field
 * name or tag number on the wire. Decoding requires a schema, and
 * Avro's container file format conveniently embeds the exact schema
 * that wrote it. That gives an old reader a genuine choice:
 *
 *   default (GENERIC): read using the file's own embedded schema,
 *     never narrowing down to v1's compiled schema at all. This
 *     produces a GenericRecord with EVERY original field, including
 *     ones v1 has never heard of.
 *
 *   --typed: explicitly resolve the file down to v1's own schema.
 *     Avro's schema-resolution logic skips any writer field not
 *     declared in the reader schema WHILE PARSING -- "email" is
 *     never read into memory in the first place, so there is nothing
 *     to carry forward.
 */
public final class Reader {

    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.avro");
        boolean typedMode = args.length > 1 && "--typed".equals(args[1]);

        if (typedMode) {
            runTypedLossyPath(dataFile.toFile());
        } else {
            runGenericSafePath(dataFile.toFile());
        }
    }

    private static void runGenericSafePath(File file) throws IOException {
        GenericRecord record;
        Schema embeddedWriterSchema;
        try (DataFileReader<GenericRecord> fileReader = new DataFileReader<>(file, new GenericDatumReader<>())) {
            embeddedWriterSchema = fileReader.getSchema();
            record = fileReader.next();
        }

        System.out.println("[Avro Reader v1] Read (no resolution, using embedded writer schema): " + record);
        explainUnknownFields(embeddedWriterSchema);

        record.put("age", (Integer) record.get("age") + 1);

        try (DataFileWriter<GenericRecord> fileWriter =
                     new DataFileWriter<>(new GenericDatumWriter<>(embeddedWriterSchema))) {
            fileWriter.create(embeddedWriterSchema, file);
            fileWriter.append(record);
        }

        System.out.println("[Avro Reader v1] Wrote back (embedded schema, preserves unknowns): " + record);
    }

    private static void runTypedLossyPath(File file) throws IOException {
        Schema v1 = AvroSchemas.v1();

        System.out.println("[Avro Reader v1] !! --typed mode: resolving the file down to v1's own schema."
                + " Avro's schema-resolution skips any writer field v1 doesn't declare WHILE PARSING --"
                + " 'email' is never read into memory at all !!");

        GenericDatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        datumReader.setExpected(v1);

        GenericRecord resolved;
        try (DataFileReader<GenericRecord> fileReader = new DataFileReader<>(file, datumReader)) {
            resolved = fileReader.next();
        }

        System.out.println("[Avro Reader v1] Read (resolved to v1 schema): " + resolved);

        resolved.put("age", (Integer) resolved.get("age") + 1);

        try (DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(new GenericDatumWriter<>(v1))) {
            fileWriter.create(v1, file);
            fileWriter.append(resolved);
        }

        System.out.println("[Avro Reader v1] Wrote back (v1 schema only, lossy): " + resolved);
    }

    private static void explainUnknownFields(Schema embeddedWriterSchema) {
        for (Schema.Field field : embeddedWriterSchema.getFields()) {
            if (!field.name().equals("name") && !field.name().equals("age")) {
                System.out.println("[Avro Reader v1] NOTE: field '" + field.name()
                        + "' is unknown to v1's compiled schema. It's visible here only because this"
                        + " reader opened the file using its EMBEDDED writer schema instead of resolving"
                        + " down to v1's own schema.");
            }
        }
    }
}
