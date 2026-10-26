package com.waltest.protoevolution.thrift;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

import java.io.FileInputStream;
import java.nio.file.Path;

/**
 * Simulates the v2 Writer re-reading the file after the v1 Reader
 * touched it, and asserting the schema-evolution contract held.
 */
public final class Verifier {

    public static void main(String[] args) throws Exception {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.thrift.bin");
        int expectedAge = Integer.parseInt(args.length > 1 ? args[1] : "31");
        String expectedEmail = args.length > 2 ? args[2] : "alice@example.com";

        PersonV2 reread = new PersonV2();
        try (FileInputStream in = new FileInputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(in);
            TProtocol protocol = new TBinaryProtocol(transport);
            reread.read(protocol);
        }

        System.out.println("[Thrift Writer] Re-read after v1 round-trip: " + reread);

        String actualEmail = reread.email == null ? "" : reread.email;
        boolean ageOk = reread.age == expectedAge;
        boolean emailOk = expectedEmail.equals(actualEmail);

        System.out.printf("  age   expected=%d actual=%d -> %s%n", expectedAge, reread.age, ageOk ? "PASS" : "FAIL");
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
