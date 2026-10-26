package com.waltest.protoevolution.thrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;

/**
 * Hand-written to match EXACTLY the shape the `thrift` compiler
 * generates for {@code struct Person { 1: string name; 2: i32 age; }}
 * -- the "v1" IDL, with no knowledge of "email" (field 3).
 *
 * This is not using a code generator; it's the same read()/write()
 * structure codegen always produces, written out so the
 * unknown-field-handling behavior (the {@code default:} branch in
 * {@link #read}) is visible and auditable instead of hidden inside
 * generated output.
 */
public class PersonV1 {

    private static final TStruct STRUCT_DESC = new TStruct("Person");
    private static final TField NAME_FIELD_DESC = new TField("name", TType.STRING, (short) 1);
    private static final TField AGE_FIELD_DESC = new TField("age", TType.I32, (short) 2);

    public String name;
    public int age;

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
                default:
                    // THIS is the line that matters: a field ID this
                    // struct doesn't declare gets read past and
                    // thrown away. Nothing stores it. There is no
                    // UnknownFieldSet equivalent in generated Thrift
                    // code -- this skip() call is the ENTIRE handling
                    // of unrecognized fields, by design.
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
        oprot.writeFieldStop();
        oprot.writeStructEnd();
    }

    @Override
    public String toString() {
        return "Person(name:" + name + ", age:" + age + ")";
    }
}
