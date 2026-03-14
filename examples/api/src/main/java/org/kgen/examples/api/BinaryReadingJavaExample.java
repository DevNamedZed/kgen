package org.kgen.examples.api;

import kotlin.Unit;
import org.kgen.binary.elf.ElfReader;
import org.kgen.binary.macho.MachOReader;
import org.kgen.binary.pe.PeReader;
import org.kgen.target.jvm.AccessFlags;
import org.kgen.target.jvm.ClassFileBuilder;
import org.kgen.target.jvm.ClassFile;
import org.kgen.target.jvm.JvmClassReader;

/**
 * Java version of the binary reading examples.
 *
 * <p>Demonstrates reading and inspecting binary files from Java.
 * kgen can parse ELF, PE/COFF, Mach-O, and JVM class files.</p>
 */
public final class BinaryReadingJavaExample {

    /**
     * Creates a sample JVM class file, then reads it back and inspects its structure.
     */
    public static void inspectClassFile() {
        ClassFileBuilder builder = new ClassFileBuilder("com/example/Calculator");
        builder.method("add", "(II)I", AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
            code.iload(0);
            code.iload(1);
            code.iadd();
            code.ireturn();
            return Unit.INSTANCE;
        });
        byte[] classBytes = builder.toBytes();

        ClassFile classFile = JvmClassReader.INSTANCE.read(classBytes);

        System.out.println("Class: " + classFile.getThisClassName());
        System.out.println("Super: " + classFile.getSuperClassName());
        System.out.println("Java version: " + classFile.getJavaVersion());
        System.out.println("Methods:");
        for (var method : classFile.getMethods()) {
            String methodName = classFile.string(method.getNameIndex());
            String descriptor = classFile.string(method.getDescriptorIndex());
            System.out.printf("  %s%s%n", methodName, descriptor);
        }
    }

    /**
     * Detects the binary format of raw bytes by checking magic numbers.
     */
    public static String detectFormat(byte[] bytes) {
        if (ElfReader.canRead(bytes)) {
            return "ELF";
        }
        if (PeReader.canRead(bytes)) {
            return "PE/COFF";
        }
        if (MachOReader.canRead(bytes)) {
            return "Mach-O";
        }
        return "Unknown";
    }

    public static void main(String[] args) {
        System.out.println("=== JVM Class File Inspection (Java) ===");
        inspectClassFile();
    }
}
