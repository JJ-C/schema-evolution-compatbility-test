package com.waltest.protoevolution.thrift;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates the service running the NEW (v2) IDL. Writes name, age,
 * and email using TBinaryProtocol -- a tag-based binary format, same
 * conceptual shape as protobuf's wire format (field ID + type + value).
 */
public final class Writer {

    public static void main(String[] args) throws Exception {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.thrift.bin");
        Files.createDirectories(dataFile.getParent());

        PersonV2 person = new PersonV2();
        person.name = "Alice";
        person.age = 30;
        person.email = "alice@example.com";

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(out);
            TProtocol protocol = new TBinaryProtocol(transport);
            person.write(protocol);
        }

        System.out.println("[Thrift Writer] Wrote: " + person);
        System.out.println("[Thrift Writer] -> " + dataFile.toAbsolutePath());
    }
}
