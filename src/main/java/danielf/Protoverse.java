package danielf;

import danielf.protobuf.Field;
import danielf.protobuf.FieldType;
import danielf.protobuf.Message;
import danielf.protobuf.Utf16ToInt32Stream;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.*;

public class Protoverse {
    private final MultiDexContainer<? extends DexBackedDexFile> multiDexContainer;
    private String generatedMessageLiteClassName;
    private String methodToInvokeEnumName;
    private final List<DexBackedClassDef> messageClassDefs = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();

    public Protoverse(MultiDexContainer<? extends DexBackedDexFile> multiDexContainer) throws IOException {
        this.multiDexContainer = multiDexContainer;

        this.findGeneratedMessageLite();
        this.findMethodToInvokeEnum();
        this.findMessageClasses();
        this.analyseMessageClassDefs();
    }

    private void findGeneratedMessageLite() throws IOException {
        for (String dexEntryName : multiDexContainer.getDexEntryNames()) {
            var dex = Objects.requireNonNull(multiDexContainer.getEntry(dexEntryName)).getDexFile();
            for (var f : dex.getFieldSection()) {
                if (f.getName().equals("MEMOIZED_SERIALIZED_SIZE_MASK")) {
                    this.generatedMessageLiteClassName = f.getDefiningClass();
                    return;
                }
            }
        }

        throw new RuntimeException("Failed to find GeneratedMessageLite class");
    }

    private void findMethodToInvokeEnum() throws IOException {
        for (String dexEntryName : multiDexContainer.getDexEntryNames()) {
            var dex = Objects.requireNonNull(multiDexContainer.getEntry(dexEntryName)).getDexFile();
            for (var classDef : dex.getClasses()) {
                // skip non-enums
                if ((classDef.getAccessFlags() & AccessFlags.ENUM.getValue()) == 0) {
                    continue;
                }

                for (var method : classDef.getMethods()) {
                    // skip non-clinit
                    if (!method.getName().equals("<clinit>")) {
                        continue;
                    }

                    var instruction = Objects.requireNonNull(method.getImplementation()).getInstructions().iterator().next();
                    if (instruction instanceof ReferenceInstruction && instruction.getOpcode() == Opcode.CONST_STRING) {
                        if (((StringReference) ((ReferenceInstruction) instruction).getReference()).getString().equals("GET_MEMOIZED_IS_INITIALIZED")) {
                            this.methodToInvokeEnumName = classDef.getType();
                            return;
                        }
                    }

                }
            }
        }

        throw new RuntimeException("Failed to find MethodToInvoke enum");
    }

    private void findMessageClasses() throws IOException {
        for (String dexEntryName : multiDexContainer.getDexEntryNames()) {
            var dex = Objects.requireNonNull(multiDexContainer.getEntry(dexEntryName)).getDexFile();
            for (var classDef : dex.getClasses()) {
                if (generatedMessageLiteClassName.equals(classDef.getSuperclass())) {
                       messageClassDefs.add(classDef);
                }
            }
        }
    }

    private void analyseMessageClassDefs() {
        for (var classDef : this.messageClassDefs) {
            try {
                this.messages.add(analyseMessageClassDef(classDef));
            } catch (RuntimeException e) {
                System.out.println("Failed to analyse " + classDef.getType());
            }
        }

        System.err.println(String.format("Analysed %d messages", messages.size()));
    }

    private Message analyseMessageClassDef(DexBackedClassDef classDef) {
        String name = nameForType(classDef.getType());
        Message message = new Message(name);

        for (var method : classDef.getVirtualMethods()) {
            var params = method.getParameterTypes();
            if (params.size() != 3) continue;
            if (!params.get(0).equals(methodToInvokeEnumName)) continue;

            analyseMessageBuildMessageInfo(message, classDef, method);
            break;
        }

        return message;
    }

    private void analyseMessageBuildMessageInfo(Message message, ClassDef classDef, DexBackedMethod method) {
        Map<String, String> classFields = new HashMap<>();
        for (var field : classDef.getFields()) {
            classFields.put(field.getName(), nameForType(field.getType()));
        }

        String[] registers = new String[Objects.requireNonNull(method.getImplementation()).getRegisterCount()];
        List<String> objects = new ArrayList<>();
        String info = null;
        for (var instruction : Objects.requireNonNull(method.getImplementation()).getInstructions()) {
            if (!(instruction instanceof OneRegisterInstruction) && instruction.getOpcode() != Opcode.INVOKE_STATIC) continue;
            int register = -1;
            if (instruction instanceof OneRegisterInstruction) {
                register = ((OneRegisterInstruction) instruction).getRegisterA();
            }
            String value = null;
            switch (instruction.getOpcode()) {
                case CONST_STRING:
                case CONST_STRING_JUMBO:
                    value = ((StringReference) ((ReferenceInstruction) instruction).getReference()).getString();
                    break;
                case CONST_CLASS:
                    value = nameForType(((TypeReference) ((ReferenceInstruction) instruction).getReference()).getType());
                    break;
                case SGET_OBJECT:
                    value = nameForType(((FieldReference) ((ReferenceInstruction) instruction).getReference()).getDefiningClass());
                    break;
                case APUT_OBJECT:
                    // Assume that the assignment is ordered
                    objects.add(Objects.requireNonNull(registers[register]));
                    break;
                case INVOKE_STATIC:
                    var methodReference = ((MethodReference) ((ReferenceInstruction) instruction).getReference());
                    if (!methodReference.getDefiningClass().equals(generatedMessageLiteClassName)) continue;
                    var infoParamIndex = methodReference.getParameterTypes().indexOf("Ljava/lang/String;");
                    int infoRegister = -1;
                    switch (infoParamIndex) {
                        case 0:
                            infoRegister = ((FiveRegisterInstruction) instruction).getRegisterC();
                            break;
                        case 1:
                            infoRegister = ((FiveRegisterInstruction) instruction).getRegisterD();
                            break;
                        case 2:
                            infoRegister = ((FiveRegisterInstruction) instruction).getRegisterE();
                            break;
                    }
                    info = registers[infoRegister];
                    break;
            }

            if (value != null) {
                registers[register] = value;
            }

            if (info != null) break;
        }

//        if (info == null) throw new RuntimeException("Failed to analyse BUILD_MESSAGE_INFO");
        if (info == null) return;


        var intStream = new Utf16ToInt32Stream(info);
        var objectIterator = objects.iterator();

        var flags = intStream.next();
        var fieldCount = intStream.next();
        if (fieldCount == 0) return;
        var oneOfCount = intStream.next();
        var hasBitsCount = intStream.next();
        var minFieldNumber = intStream.next();
        var maxFieldNumber = intStream.next();
        var entriesToAllocate = intStream.next();
        var mapFieldCount = intStream.next();
        var repeatedFieldCount = intStream.next();
        var checkInitializedArraySize = intStream.next();

        if (oneOfCount != 0) throw new RuntimeException("Oneof not supported");

        // skip hasBit fields
        for (int i = 1; i < hasBitsCount; ++i) {
            objectIterator.next();
        }

        for (int i = 0; i < fieldCount; ++i) {
            int fieldNumber = intStream.next();
            int fieldTypeExtra = intStream.next();
            FieldType fieldType = FieldType.forId(fieldTypeExtra & 0xFF);
            assert fieldType != null;

            String fieldName = objectIterator.next();
            boolean repeated = fieldType.isList();
            boolean isEnum = fieldType == FieldType.ENUM || fieldType == FieldType.ENUM_LIST || fieldType == FieldType.ENUM_LIST_PACKED;
            if (fieldType.isMap()) {
                throw new RuntimeException("Maps not unsupported");
            }
            // presence checking support
            if ((fieldTypeExtra & 0x1000) == 0x1000) {
                intStream.next();
            }
            String fieldTypeName;
            if (isEnum || fieldType == FieldType.MESSAGE_LIST) {
                fieldTypeName = objectIterator.next();
            } else if (fieldType == FieldType.MESSAGE ) {
                fieldTypeName = classFields.get(fieldName);
            } else {
                fieldTypeName = fieldType.name().split("_")[0].toLowerCase();
            }
            Field field = new Field(fieldName.substring(0, fieldName.length() - 1), fieldTypeName, repeated);
            message.setField(fieldNumber, field);
        }
    }

    private static String nameForType(String type) {
        var parts = type.split("/");
        var last = parts[parts.length - 1];
        return "X_" + last.substring(0, last.length() - 1);
    }

    public void dump(PrintStream stream) throws IOException {
        stream.println("syntax = \"proto3\";\n");
        for (var message : this.messages) {
            stream.println(message.toString());
        }
    }
}
