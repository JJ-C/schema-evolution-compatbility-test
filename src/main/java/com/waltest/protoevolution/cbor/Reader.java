package com.waltest.protoevolution.cbor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.waltest.protoevolution.jackson.ScenarioReader;

import java.io.IOException;
import java.nio.file.Path;

public final class Reader {
    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.cbor");
        boolean typedMode = args.length > 1 && "--typed".equals(args[1]);

        ScenarioReader.read(new CBORMapper(),
                () -> CBORMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
                dataFile, typedMode, "CBOR");
    }
}
