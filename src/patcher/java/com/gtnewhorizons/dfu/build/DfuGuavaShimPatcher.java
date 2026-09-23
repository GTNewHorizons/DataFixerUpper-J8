package com.gtnewhorizons.dfu.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

public final class DfuGuavaShimPatcher {

    private static final String COMPAT = "com/gtnewhorizons/dfu/GuavaCompat";
    private static final String IMMUTABLE_LIST = "com/google/common/collect/ImmutableList";
    private static final String IMMUTABLE_MAP = "com/google/common/collect/ImmutableMap";
    private static final String IMMUTABLE_MAP_BUILDER = "com/google/common/collect/ImmutableMap$Builder";
    private static final String TYPE_TOKEN = "com/google/common/reflect/TypeToken";
    private static final String SUPPLIER = "com/google/common/base/Supplier";

    // 1.7.10 has no slf4j, so DFU's logging is redirected to log4j2.
    private static final String LOGGER = "com/gtnewhorizons/dfu/DfuLogger";
    private static final Remapper SLF4J_REMAPPER = new SimpleRemapper(Map.of(
            "org/slf4j/Logger", LOGGER,
            "org/slf4j/LoggerFactory", "com/gtnewhorizons/dfu/DfuLoggerFactory"));
    private static final Set<String> LOGGER_METHODS = Set.of(
            "info(Ljava/lang/String;)V",
            "info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
            "warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
            "error(Ljava/lang/String;)V");

    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("mapBuilder", 2);
        EXPECTED.put("mapBuilderWithExpectedSize", 3);
        EXPECTED.put("listBuilderWithExpectedSize", 1);
        EXPECTED.put("buildKeepingLast", 5);
        EXPECTED.put("toImmutableMap", 2);
        EXPECTED.put("isSupertypeOf", 2);
        EXPECTED.put("memoize", 2);
    }

    private DfuGuavaShimPatcher() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected input jar and output directory");
        }

        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Map<String, Integer> replacements = new LinkedHashMap<>();
        for (String name : EXPECTED.keySet()) {
            replacements.put(name, 0);
        }

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(Paths.get(args[0])))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = output.resolve(entry.getName()).normalize();
                if (!target.startsWith(output)) {
                    throw new IOException("Invalid DFU entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                byte[] contents = zip.readAllBytes();
                if (entry.getName().startsWith("com/mojang/") && entry.getName().endsWith(".class")) {
                    contents = patchClass(contents, replacements);
                }
                Files.createDirectories(target.getParent());
                Files.write(target, contents);
            }
        }

        if (!EXPECTED.equals(replacements)) {
            throw new IllegalStateException(
                    "Unexpected DFU Guava call sites: " + replacements + ", expected " + EXPECTED);
        }
    }

    private static byte[] patchClass(byte[] contents, Map<String, Integer> replacements) {
        ClassReader reader = new ClassReader(contents);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9, writer) {

            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(IMMUTABLE_MAP)
                                && name.equals("builder")
                                && descriptor.equals("()L" + IMMUTABLE_MAP_BUILDER + ";")
                                && (className.equals("com/mojang/serialization/RecordBuilder$MapBuilder")
                                        || className.equals("com/mojang/serialization/JavaOps$FixedMapBuilder"))) {
                            replace("mapBuilder", descriptor);
                            return;
                        }
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(IMMUTABLE_MAP)
                                && name.equals("builderWithExpectedSize")
                                && descriptor.equals("(I)L" + IMMUTABLE_MAP_BUILDER + ";")) {
                            replace("mapBuilderWithExpectedSize", descriptor);
                            return;
                        }
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(IMMUTABLE_LIST)
                                && name.equals("builderWithExpectedSize")
                                && descriptor.equals("(I)Lcom/google/common/collect/ImmutableList$Builder;")) {
                            replace("listBuilderWithExpectedSize", descriptor);
                            return;
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(IMMUTABLE_MAP_BUILDER)
                                && name.equals("buildKeepingLast")
                                && descriptor.equals("()L" + IMMUTABLE_MAP + ";")) {
                            replace("buildKeepingLast", "(L" + IMMUTABLE_MAP_BUILDER + ";)L" + IMMUTABLE_MAP + ";");
                            return;
                        }
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(IMMUTABLE_MAP)
                                && name.equals("toImmutableMap")
                                && descriptor.equals(
                                        "(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/stream/Collector;")) {
                            replace("toImmutableMap", descriptor);
                            return;
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(TYPE_TOKEN)
                                && name.equals("isSupertypeOf")
                                && descriptor.equals("(L" + TYPE_TOKEN + ";)Z")) {
                            replace("isSupertypeOf", "(L" + TYPE_TOKEN + ";L" + TYPE_TOKEN + ";)Z");
                            return;
                        }
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("com/google/common/base/Suppliers")
                                && name.equals("memoize")
                                && descriptor.equals("(L" + SUPPLIER + ";)L" + SUPPLIER + ";")) {
                            replace("memoize", "(L" + SUPPLIER + ";)Ljava/util/function/Supplier;");
                            return;
                        }
                        checkLogger(owner, name, descriptor);
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments) {
                        for (Object arg : bootstrapMethodArguments) {
                            if (arg instanceof Handle handle) {
                                checkLogger(handle.getOwner(), handle.getName(), handle.getDesc());
                            }
                        }
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    }

                    private void checkLogger(String owner, String name, String descriptor) {
                        if (owner.equals(LOGGER) && !LOGGER_METHODS.contains(name + descriptor)) {
                            throw new IllegalStateException(
                                    "DfuLogger lacks slf4j method " + name + descriptor + " used in " + className);
                        }
                    }

                    private void replace(String name, String descriptor) {
                        replacements.put(name, replacements.get(name) + 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, name, descriptor, false);
                    }
                };
            }
        }, SLF4J_REMAPPER), 0);
        return writer.toByteArray();
    }
}
