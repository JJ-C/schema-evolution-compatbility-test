package com.waltest.protoevolution.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.waltest.protoevolution.jackson.ScenarioReader;
import com.waltest.protoevolution.jackson.ScenarioReader.Mode;

import java.io.IOException;
import java.nio.file.Path;

public final class Reader {
    public static void main(String[] args) throws IOException {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "data/person.json");
        Mode mode = switch (args.length > 1 ? args[1] : "") {
            case "--typed" -> Mode.TYPED_LOSSY;
            case "--overflow" -> Mode.TYPED_OVERFLOW;
            default -> Mode.GENERIC;
        };

        ScenarioReader.read(new ObjectMapper(),
                () -> JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
                dataFile, mode, "JSON");
    }
}
