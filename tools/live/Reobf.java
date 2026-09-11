import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** D3 reobfuscator (same tool as C3): reobfuscate Mojmap-named member refs
 * to SRG (ForgeGradle reobf equivalent). Production classes stay Mojmap
 * (the installer keeps classes official), so only narrow-map members move;
 * Forge and mod classes pass through untouched. Compiled against the
 * provisioned ASM (asm-9.5 + asm-commons-9.5 on 47.2.0).
 *
 * <p>Invokedynamic SAMs (hub decisions/SPAWN.md, custom entity tranche):
 * a lambda/method-ref names its SAM in the COMPILED namespace, so an
 * indy targeting a reobfuscated vanilla SAM must ship the SRG name —
 * otherwise LambdaMetafactory spins a class implementing a method the
 * runtime interface does not declare (AbstractMethodError, measured live
 * on 47.2.0 for both the renderer provider and the entity factory —
 * never silent). The rewrite consults the same MD lines by
 * (SAM-name, SAM-desc) with the owner ignored (an indy carries none);
 * (name, desc) must be unique across the map — ambiguity refuses loudly,
 * never a guess. Anything without an exact hit passes through (java.util
 * SAMs, project SAMs). */
public final class Reobf {
    public static void main(String[] a) throws Exception {
        if (a.length != 3) {
            System.err.println("usage: Reobf <srg-mcp.srg> <in.jar> <out.jar>");
            System.exit(1);
        }
        Map<String, String> methods = new HashMap<String, String>();
        Map<String, String> fields = new HashMap<String, String>();
        Map<String, String> samMethods = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new FileReader(a[0]));
        String line;
        while ((line = br.readLine()) != null) {
            String[] t = line.split(" ");
            if (t[0].equals("MD:") && t.length == 5) {
                methods.put(mcpKey(t[3], t[4]), simple(t[1]));
                String samKey = t[3].substring(t[3].lastIndexOf('/') + 1) + t[4];
                if (samMethods.containsKey(samKey)) {
                    System.err.println("E_REOBF:sam-ambiguous <" + samKey + "> (two narrow members share one SAM shape)");
                    System.exit(1);
                }
                samMethods.put(samKey, simple(t[1]));
            } else if (t[0].equals("FD:") && t.length == 3) {
                String fqn = t[2];
                int s = fqn.lastIndexOf('/');
                fields.put(fqn.substring(0, s) + "." + fqn.substring(s + 1), simple(t[1]));
            }
        }
        br.close();
        System.err.println("map: " + methods.size() + " methods, " + fields.size() + " fields");
        final Map<String, String> m = methods;
        final Map<String, String> f = fields;
        final Map<String, String> samM = samMethods;
        Remapper remapper = new Remapper() {
            public String mapMethodName(String owner, String name, String desc) {
                // Mojang owners remap like vanilla ones (the renderer
                // tranche's PoseStack.last/pose are com/mojang with SRG
                // members at runtime — a net-only gate would pass them
                // through silently to die linking live; caught offline at
                // E0 by reobfing the staged jar and grepping the pool).
                if (owner.startsWith("net/minecraft/")
                        || owner.startsWith("com/mojang/")) {
                    String hit = m.get(owner + "." + name + desc);
                    if (hit != null) {
                        return hit;
                    }
                }
                return name;
            }
            public String mapFieldName(String owner, String name, String desc) {
                if (owner.startsWith("net/minecraft/")
                        || owner.startsWith("com/mojang/")) {
                    String hit = f.get(owner + "." + name);
                    if (hit != null) {
                        return hit;
                    }
                }
                return name;
            }
        };
        JarFile in = new JarFile(a[1]);
        JarOutputStream out = new JarOutputStream(new FileOutputStream(a[2]));
        Enumeration<JarEntry> en = in.entries();
        int remappedRefs = 0;
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            InputStream is = in.getInputStream(e);
            byte[] data = readAll(is);
            is.close();
            JarEntry ne = new JarEntry(e.getName());
            ne.setTime(e.getTime());
            out.putNextEntry(ne);
            if (e.getName().endsWith(".class")) {
                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassRemapper(cw, remapper) {
                    protected MethodVisitor createMethodRemapper(
                            MethodVisitor mv) {
                        return new MethodRemapper(mv, remapper) {
                            public void visitInvokeDynamicInsn(String name,
                                    String descriptor, Handle bsm,
                                    Object... bsmArgs) {
                        // SAM-name rewrite (see the class note): scan the
                        // bootstrap method-Type args (samMethodType first,
                        // instantiatedMethodType beside it) for an exact
                        // (name, SAM-desc) hit. Zero hits pass through;
                        // two different hits refuse loudly.
                        java.util.Set<String> found =
                                new java.util.HashSet<String>();
                        for (Object arg : bsmArgs) {
                            if (arg instanceof Type
                                    && ((Type) arg).getSort()
                                            == Type.METHOD) {
                                String hit = samM.get(name
                                        + ((Type) arg).getDescriptor());
                                if (hit != null) {
                                    found.add(hit);
                                }
                            }
                        }
                        if (found.size() > 1) {
                            throw new IllegalStateException(
                                    "E_REOBF:sam-ambiguous-indy <" + name
                                            + descriptor + "> " + found);
                        }
                        super.visitInvokeDynamicInsn(
                                found.size() == 1
                                        ? found.iterator().next() : name,
                                descriptor, bsm, bsmArgs);
                    }
                    };
                    }
                }, ClassReader.EXPAND_FRAMES);
                out.write(cw.toByteArray());
            } else {
                out.write(data);
            }
            out.closeEntry();
        }
        in.close();
        out.close();
        System.out.println("ok reobf : " + a[2]);
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('/') + 1);
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static String mcpKey(String mcpFqn, String mcpDesc) {
        int s = mcpFqn.lastIndexOf('/');
        return mcpFqn.substring(0, s) + "." + mcpFqn.substring(s + 1) + mcpDesc;
    }
}
