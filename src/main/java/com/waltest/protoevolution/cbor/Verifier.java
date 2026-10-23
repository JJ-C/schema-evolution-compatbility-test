package com.waltest.protoevolution.cbor;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.waltest.protoevolution.jackson.ScenarioVerifier;

import java.io.IOException;
import java.nio.file.Path;

public final class Verifier {
    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.cbor");
        int expectedAge = Integer.parseInt(args.length > 1 ? args[1] : "31");
        String expectedEmail = args.length > 2 ? args[2] : "alice@example.com";
        ScenarioVerifier.verify(new CBORMapper(), dataFile, expectedAge, expectedEmail, "CBOR");
    }
}
