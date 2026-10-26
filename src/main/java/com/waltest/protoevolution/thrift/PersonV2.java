package com.waltest.protoevolution.thrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;

/**
 * Hand-written to match the `thrift` compiler's output for
 * {@code struct Person { 1: string name; 2: i32 age; 3: string email; }}
 * -- the "v2" IDL. Used by the Writer (to produce the file) and the
 * Verifier (to confirm what the file contains after the v1 Reader's
 * round-trip).
 */
public class PersonV2 {

    private static final TStruct STRUCT_DESC = new TStruct("Person");
    private static final TField NAME_FIELD_DESC = new TField("name", TType.STRING, (short) 1);
    private static final TField AGE_FIELD_DESC = new TField("age", TType.I32, (short) 2);
    private static final TField EMAIL_FIELD_DESC = new TField("email", TType.STRING, (short) 3);

    public String name;
    public int age;
    public String email;

    public void read(TProtocol iprot) throws TException {
        iprot.readStructBegin();
        while (true) {
            TField field = iprot.readFieldBegin();
            if (field.type == TType.STOP) {
                break;
            }
            switch (field.id) {
                case 1:
                    if (field.type == TType.STRING) {
                        name = iprot.readString();
                    } else {
                        TProtocolUtil.skip(iprot, field.type);
                    }
                    break;
                case 2:
                    if (field.type == TType.I32) {
                        age = iprot.readI32();
                    } else {
                        TProtocolUtil.skip(iprot, field.type);
                    }
                    break;
                case 3:
                    if (field.type == TType.STRING) {
                        email = iprot.readString();
                    } else {
                        TProtocolUtil.skip(iprot, field.type);
                    }
                    break;
                default:
                    TProtocolUtil.skip(iprot, field.type);
                    break;
            }
            iprot.readFieldEnd();
        }
        iprot.readStructEnd();
    }

    public void write(TProtocol oprot) throws TException {
        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldBegin(NAME_FIELD_DESC);
        oprot.writeString(name);
        oprot.writeFieldEnd();
        oprot.writeFieldBegin(AGE_FIELD_DESC);
        oprot.writeI32(age);
        oprot.writeFieldEnd();
        oprot.writeFieldBegin(EMAIL_FIELD_DESC);
        oprot.writeString(email);
        oprot.writeFieldEnd();
        oprot.writeFieldStop();
        oprot.writeStructEnd();
    }

    @Override
    public String toString() {
        return "Person(name:" + name + ", age:" + age + ", email:" + email + ")";
    }
}
