package com.waltest.protoevolution.thrift;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;

/**
 * Simulates the service still running the OLD (v1) IDL.
 *
 * Unlike protobuf, standard generated Thrift code has NO equivalent
 * of an UnknownFieldSet. Reading with a struct that doesn't declare a
 * field causes that field to be skipped and discarded -- permanently
 * -- the moment parsing happens. This is true regardless of what you
 * do afterward; the data is gone before your code ever runs.
 *
 *   default (GENERATED): uses PersonV1, a struct exactly like what
 *     `thrift` codegen produces for the v1 IDL. This is the ORDINARY,
 *     unmodified way to use Thrift. It is LOSSY BY DEFAULT -- no flag
 *     needed to break it, unlike every other format in this suite.
 *
 *   --manual-preserve: uses GenericThriftStruct, a hand-rolled
 *     generic reader that never narrows to a fixed struct shape. This
 *     is NOT something Thrift provides; it's bespoke code written
 *     specifically to recover the preservation guarantee protobuf
 *     gives for free.
 */
public final class Reader {

    public static void main(String[] args) throws Exception {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.thrift.bin");
        boolean manualPreserve = args.length > 1 && "--manual-preserve".equals(args[1]);

        if (manualPreserve) {
            runManualPreservePath(dataFile);
        } else {
            runGeneratedLossyPath(dataFile);
        }
    }

    private static void runGeneratedLossyPath(Path dataFile) throws Exception {
        PersonV1 person = new PersonV1();
        try (FileInputStream in = new FileInputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(in);
            TProtocol protocol = new TBinaryProtocol(transport);
            person.read(protocol);
        }

        System.out.println("[Thrift Reader v1] Read (generated v1 struct): " + person);
        System.out.println("[Thrift Reader v1] NOTE: field 3 ('email') was present on the wire, but"
                + " PersonV1.read() has no case for field ID 3 -- TProtocolUtil.skip() already discarded"
                + " it while parsing, above. It was never held in memory at any point in this method.");

        person.age = person.age + 1;

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(out);
            TProtocol protocol = new TBinaryProtocol(transport);
            person.write(protocol);
        }

        System.out.println("[Thrift Reader v1] Wrote back (generated v1 struct, lossy by default): " + person);
    }

    private static void runManualPreservePath(Path dataFile) throws Exception {
        GenericThriftStruct struct;
        try (FileInputStream in = new FileInputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(in);
            TProtocol protocol = new TBinaryProtocol(transport);
            struct = GenericThriftStruct.readFrom(protocol);
        }

        System.out.println("[Thrift Reader v1] Read (hand-rolled generic struct): " + struct);
        if (struct.has((short) 3)) {
            System.out.println("[Thrift Reader v1] NOTE: field ID 3 is unknown to v1's compiled IDL."
                    + " Thrift's binary protocol has no field NAMES on the wire, only numeric IDs -- this"
                    + " generic reader can carry the value forward, but it genuinely cannot tell you what"
                    + " the field is called, only that field 3 exists.");
        }

        int currentAge = (Integer) struct.get((short) 2);
        struct.put((short) 2, TType.I32, currentAge + 1);

        try (FileOutputStream out = new FileOutputStream(dataFile.toFile())) {
            TTransport transport = new TIOStreamTransport(out);
            TProtocol protocol = new TBinaryProtocol(transport);
            struct.writeTo(protocol);
        }

        System.out.println("[Thrift Reader v1] Wrote back (hand-rolled generic struct, preserves unknowns): "
                + struct);
    }
}
