package com.github.skrcode.javaautounittests;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntryTable;

import java.lang.reflect.Field;

/**
 * Expands all star imports (normal + static) for the file that contains the
 * given {@link PsiClass}, relying 100 % on IntelliJ’s own code-style engine.
 *
 * ✓ No custom heuristics or manual AST walks.
 * ✓ Works on every IntelliJ SDK version (old String[] vs new PackageEntryTable).
 * ✓ Operates in-memory; nothing is flushed to disk unless the user saves.
 * ✓ Restores every code-style preference right after the rewrite.
 */
public final class CUTUtil {
    private CUTUtil() {}

    public static String findMockitoVersion(Project project) {
        LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        for (Library lib : table.getLibraries()) {
            String name = lib.getName();
            if (name != null && name.toLowerCase().contains("mockito")) {
                // e.g. "Gradle: org.mockito:mockito-core:5.12.0"
                String[] parts = name.split(":");
                if (parts.length >= 3) {
                    return parts[parts.length - 1]; // the version string
                }
            }
        }
        return ""; // not found
    }

    /* ---------------------------------------------------------------------- */
    /** Expands all star imports.  Call this inside your plugin before
     serialising the file for an LLM. */
    public static void expandAll(Project project, PsiJavaFile file) {
        WriteCommandAction.runWriteCommandAction(project, () -> {

            JavaCodeStyleSettings js = CodeStyle.getSettings(file)
                    .getCustomSettings(JavaCodeStyleSettings.class);

            /* 1️⃣  Snapshot user preferences */
            int prevClass  = js.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
            int prevNames  = js.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
            int prevStatic = snapshotStaticThreshold(js);       // −1 if field absent
            Object starPkgSnapshot = snapshotStarPackages(js);  // String[] OR table copy

            /* 2️⃣  Disable every rule that keeps ‘.*’ */
            js.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.MAX_VALUE;
            js.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.MAX_VALUE;
            if (prevStatic != -1) setStaticThreshold(js, Integer.MAX_VALUE);
            setEmptyStarPackages(js);                           // <── key change

            /* 3️⃣  Let IntelliJ expand & add imports */
            JavaCodeStyleManager mgr = JavaCodeStyleManager.getInstance(project);
            mgr.optimizeImports(file);
            mgr.shortenClassReferences(file);

            /* 4️⃣  Restore user preferences */
            js.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = prevClass;
            js.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = prevNames;
            if (prevStatic != -1) setStaticThreshold(js, prevStatic);
            restoreStarPackages(js, starPkgSnapshot);
        });
    }

    /* --------------------   Reflection helpers   ------------------------- */

    private static int snapshotStaticThreshold(JavaCodeStyleSettings js) {
        try {
            Field f = JavaCodeStyleSettings.class
                    .getField("NAMES_COUNT_TO_USE_STATIC_IMPORT_ON_DEMAND");
            return f.getInt(js);
        } catch (NoSuchFieldException ignored) {
            return -1;          // old SDK: field absent
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private static void setStaticThreshold(JavaCodeStyleSettings js, int v) {
        try {
            Field f = JavaCodeStyleSettings.class
                    .getField("NAMES_COUNT_TO_USE_STATIC_IMPORT_ON_DEMAND");
            f.setInt(js, v);
        } catch (ReflectiveOperationException ignored) {}
    }

    /** Deep-copies the current star-package list (works on any SDK). */
    private static Object snapshotStarPackages(JavaCodeStyleSettings js) {
        try {
            Field f = JavaCodeStyleSettings.class
                    .getField("PACKAGES_TO_USE_IMPORT_ON_DEMAND");
            Object val = f.get(js);
            if (val instanceof PackageEntryTable table) {
                PackageEntryTable copy = new PackageEntryTable();
                copy.copyFrom(table);
                return copy;
            }
            if (val instanceof String[] arr) {
                return arr.clone();
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Sets the star-package list to an *empty* value, regardless of type. */
    private static void setEmptyStarPackages(JavaCodeStyleSettings js) {
        try {
            Field f = JavaCodeStyleSettings.class
                    .getField("PACKAGES_TO_USE_IMPORT_ON_DEMAND");
            if (PackageEntryTable.class.isAssignableFrom(f.getType())) {
                f.set(js, new PackageEntryTable());          // new SDKs
            } else {
                f.set(js, new String[0]);                    // old SDKs
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Restores the user’s original star-package list. */
    private static void restoreStarPackages(JavaCodeStyleSettings js, Object snapshot) {
        if (snapshot == null) return;
        try {
            Field f = JavaCodeStyleSettings.class
                    .getField("PACKAGES_TO_USE_IMPORT_ON_DEMAND");
            if (snapshot instanceof PackageEntryTable snapTbl) {          // new SDKs
                ((PackageEntryTable) f.get(js)).copyFrom(snapTbl);
            } else {                                                      // old SDKs
                f.set(js, snapshot);                                      // String[]
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
    public static String cleanedSourceForLLM(Project project, PsiClass cut) {

        PsiJavaFile original = ReadAction.compute(() -> {
            if (!cut.isValid()) return null;
            PsiJavaFile file = (PsiJavaFile) cut.getContainingFile();
            return file != null ? file : null;
        });
        if(original == null)
            return "<no file>";

        // 1️⃣  Create a NON-PHYSICAL copy (no VirtualFile, never saved)
        PsiJavaFile scratch  = (PsiJavaFile) original.copy();

        // 2️⃣  Expand star imports on the scratch only
        expandAll(project, scratch);          // ← your expander from earlier

        // 3️⃣  Harvest the source and return; the scratch is GC-eligible
        return scratch.getText();
    }
}
