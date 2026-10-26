package com.waltest.protoevolution.avro;

import org.apache.avro.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the v1 and v2 Avro schemas bundled as classpath resources. */
public final class AvroSchemas {

    private AvroSchemas() {
    }

    public static Schema v1() {
        return load("/avro/v1/person.avsc");
    }

    public static Schema v2() {
        return load("/avro/v2/person.avsc");
    }

    private static Schema load(String resourcePath) {
        try (InputStream in = AvroSchemas.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema resource: " + resourcePath);
            }
            return new Schema.Parser().parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
