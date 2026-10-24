package com.waltest.protoevolution.cbor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.waltest.protoevolution.jackson.ScenarioReader;
import com.waltest.protoevolution.jackson.ScenarioReader.Mode;

import java.io.IOException;
import java.nio.file.Path;

public final class Reader {
    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.cbor");
        Mode mode = switch (args.length > 1 ? args[1] : "") {
            case "--typed" -> Mode.TYPED_LOSSY;
            case "--overflow" -> Mode.TYPED_OVERFLOW;
            default -> Mode.GENERIC;
        };

        ScenarioReader.read(new CBORMapper(),
                () -> CBORMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
                dataFile, mode, "CBOR");
    }
}
