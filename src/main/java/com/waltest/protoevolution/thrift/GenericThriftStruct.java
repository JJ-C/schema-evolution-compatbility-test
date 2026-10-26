package com.waltest.protoevolution.thrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A hand-rolled stand-in for what protobuf's {@code DynamicMessage}
 * or Avro's {@code GenericRecord} give you for free: a generic,
 * schema-less way to read EVERY field off the wire -- known or not --
 * so none of them get dropped on a round trip.
 *
 * Apache Thrift ships no equivalent of this. If you want
 * forward-compatible unknown-field preservation in Thrift, you write
 * this yourself; there is no built-in "parse without a schema" option.
 *
 * Note: only {@code TType.STRING} and {@code TType.I32} are handled,
 * which is all this demo's schema needs. A production-grade version
 * would need a case for every {@code TType} (bool, byte, double,
 * list, map, struct, etc.).
 */
public final class GenericThriftStruct {

    public record FieldValue(byte type, Object value) {
    }

    private final Map<Short, FieldValue> fields = new LinkedHashMap<>();

    public static GenericThriftStruct readFrom(TProtocol iprot) throws TException {
        GenericThriftStruct struct = new GenericThriftStruct();
        iprot.readStructBegin();
        while (true) {
            TField field = iprot.readFieldBegin();
            if (field.type == TType.STOP) {
                break;
            }
            Object value = switch (field.type) {
                case TType.STRING -> iprot.readString();
                case TType.I32 -> iprot.readI32();
                default -> throw new UnsupportedOperationException(
                        "This demo only handles STRING/I32, got TType " + field.type);
            };
            struct.fields.put(field.id, new FieldValue(field.type, value));
            iprot.readFieldEnd();
        }
        iprot.readStructEnd();
        return struct;
    }

    public void writeTo(TProtocol oprot) throws TException {
        oprot.writeStructBegin(new TStruct("Person"));
        for (Map.Entry<Short, FieldValue> entry : fields.entrySet()) {
            short id = entry.getKey();
            FieldValue fv = entry.getValue();
            oprot.writeFieldBegin(new TField("", fv.type(), id));
            switch (fv.type()) {
                case TType.STRING -> oprot.writeString((String) fv.value());
                case TType.I32 -> oprot.writeI32((Integer) fv.value());
                default -> throw new UnsupportedOperationException(
                        "This demo only handles STRING/I32, got TType " + fv.type());
            }
            oprot.writeFieldEnd();
        }
        oprot.writeFieldStop();
        oprot.writeStructEnd();
    }

    public Object get(short fieldId) {
        FieldValue fv = fields.get(fieldId);
        return fv == null ? null : fv.value();
    }

    public void put(short fieldId, byte type, Object value) {
        fields.put(fieldId, new FieldValue(type, value));
    }

    public boolean has(short fieldId) {
        return fields.containsKey(fieldId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Person{");
        fields.forEach((id, fv) -> sb.append(id).append(":").append(fv.value()).append(", "));
        return sb.append("}").toString();
    }
}
