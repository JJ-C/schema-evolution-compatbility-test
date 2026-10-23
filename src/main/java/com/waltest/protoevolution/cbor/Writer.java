package com.waltest.protoevolution.cbor;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.waltest.protoevolution.jackson.ScenarioWriter;

import java.io.IOException;
import java.nio.file.Path;

public final class Writer {
    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.cbor");
        ScenarioWriter.write(new CBORMapper(), dataFile, "CBOR");
    }
}
