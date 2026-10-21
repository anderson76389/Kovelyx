/*
 * Kovelyx Media3 reproducible binary compatibility patch.
 * The original AAR files are retained unmodified. Only two verified methods
 * are changed; refuse unexpected upstream binaries or method layouts.
 */
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.tools.ToolProvider;

public final class Media3AarPatcher {
    private static final String ALLOC = "androidx/media3/exoplayer/upstream/DefaultAllocator";
    private static final String LOCAL = "androidx/media3/datasource/LocalhostZeroCopyDataSource";
    private static final String HELPER = "androidx/media3/datasource/KovelyxHttpStatus";
    private static final String EXPECT_ALLOC = "d8c5a4357c4672c289164597d0eb1a5088daffdb1b1bdb94fcf5899fc4ad4a95";
    private static final String EXPECT_LOCAL = "5375c0ff43784d210a69e7bfc438b9da9b2f42d6fb3b967ddae9b55902c7bf3d";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: Media3AarPatcher.java INPUT_LIBS OUTPUT_DIR HELPER_SOURCE.java");
        }
        Path input = Paths.get(args[0]).toAbsolutePath();
        Path output = Paths.get(args[1]).toAbsolutePath();
        Path helperSource = Paths.get(args[2]).toAbsolutePath();
        Files.createDirectories(output);
        Path helperDir = Files.createTempDirectory("kovelyx-media3-helper-");
        try {
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("Le JDK est requis, pas un JRE");
            int exit = compiler.run(null, System.out, System.err,
                "--release", "8", "-d", helperDir.toString(), helperSource.toString());
            if (exit != 0) throw new IllegalStateException("Compilation helper HTTP échouée: " + exit);
            byte[] helper = Files.readAllBytes(helperDir.resolve(HELPER + ".class"));
            patch(input.resolve("lib-exoplayer-release.aar"),
                output.resolve("lib-exoplayer-release.aar"), EXPECT_ALLOC, ALLOC, null);
            patch(input.resolve("lib-datasource-release.aar"),
                output.resolve("lib-datasource-release.aar"), EXPECT_LOCAL, LOCAL, helper);
        } finally {
            try (var files = Files.walk(helperDir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        }
    }

    private static void patch(Path original, Path output, String expectedSha, String target, byte[] helper) throws Exception {
        byte[] bytes = Files.readAllBytes(original);
        String actual = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!actual.equals(expectedSha))
            throw new IllegalStateException("AAR changé, patch refusé : " + original + " sha256=" + actual);
        Path temp = Files.createTempFile(output.getParent(), "kovelyx-aar-", ".tmp");
        int found = 0;
        try {
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    String name = entry.getName();
                    byte[] data = in.readAllBytes();
                    if (name.equals("classes.jar")) {
                        data = patchJar(data, target, helper);
                        found++;
                    }
                    ZipEntry next = new ZipEntry(name);
                    next.setTime(0L);
                    out.putNextEntry(next);
                    if (!entry.isDirectory()) out.write(data);
                    out.closeEntry();
                }
            }
            if (found != 1) throw new IllegalStateException("classes.jar manquant/dupliqué : " + original);
            try { Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING); }
            System.out.println("MEDIA3_PATCH_OK " + output + " source=" + actual +
                " patched=" + hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output))));
        } finally { Files.deleteIfExists(temp); }
    }

    private static byte[] patchJar(byte[] jarBytes, String target, byte[] helper) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jarBytes));
             ZipOutputStream out = new ZipOutputStream(buffer)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] data = in.readAllBytes();
                if (name.equals(target + ".class")) {
                    data = changeClass(data, target);
                    found++;
                }
                if (helper != null && name.equals(HELPER + ".class"))
                    throw new IllegalStateException("Classe helper déjà présente dans le JAR source");
                ZipEntry next = new ZipEntry(name);
                next.setTime(0L);
                out.putNextEntry(next);
                if (!entry.isDirectory()) out.write(data);
                out.closeEntry();
            }
            if (helper != null) {
                ZipEntry added = new ZipEntry(HELPER + ".class");
                added.setTime(0L);
                out.putNextEntry(added);
                out.write(helper);
                out.closeEntry();
            }
        }
        if (found != 1) throw new IllegalStateException("Classe cible absente/dupliquée : " + target);
        return buffer.toByteArray();
    }

    private static byte[] changeClass(byte[] original, String type) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] changes = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM7, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (type.equals(ALLOC) && name.equals("release") &&
                    (descriptor.equals("(Landroidx/media3/exoplayer/upstream/Allocation;)V") ||
                     descriptor.equals("(Landroidx/media3/exoplayer/upstream/Allocator$AllocationNode;)V"))) {
                    return new MethodVisitor(Opcodes.ASM7, mv) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                // Re-evaluate target retention AFTER the newly released buffer
                                // enters the pool, including when reset() happened beforehand.
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ALLOC, "trim", "()V", false);
                                changes[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if (type.equals(LOCAL) && name.equals("open") &&
                    descriptor.equals("(Landroidx/media3/datasource/DataSpec;)J")) {
                    return new MethodVisitor(Opcodes.ASM7, mv) {
                        @Override public void visitIntInsn(int opcode, int operand) {
                            if (opcode == Opcodes.SIPUSH && operand == 400) {
                                // Bytecode inspection of the pinned AAR shows that local #16
                                // holds the full response header at this precise instruction.
                                super.visitVarInsn(Opcodes.ALOAD, 16);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                                    "fromHeaders", "(Ljava/lang/String;)I", false);
                                changes[0]++;
                            } else super.visitIntInsn(opcode, operand);
                        }
                    };
                }
                return mv;
            }
        };
        reader.accept(visitor, 0);
        int expected = type.equals(ALLOC) ? 2 : 1;
        if (changes[0] != expected)
            throw new IllegalStateException("Modification inattendue dans " + type + " : " + changes[0] + "/" + expected);
        return writer.toByteArray();
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 255));
        return sb.toString();
    }
}
