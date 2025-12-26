// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.util;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.file.Paths;

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
        WriteCommandAction.runWriteCommandAction(project, () -> {
                    expandAll(project, scratch);          // ← your expander from earlier
                });

        // 3️⃣  Harvest the source and return; the scratch is GC-eligible
        return scratch.getText();
    }

    public static @Nullable PsiDirectory resolveTestPackageDir(Project project,
                                                                PsiDirectory testRoot,
                                                                PsiClass cut) throws Exception {
        String relPath = getTestRelativePath(cut);
        return getOrCreateSubdirectoryPath(project, testRoot, relPath);
    }

    public static @Nullable String getTestRelativePath(PsiClass cut) {
        PsiPackage cutPkg = ReadAction.compute(() ->
                JavaDirectoryService.getInstance().getPackage(cut.getContainingFile().getContainingDirectory())
        );
        if (cutPkg == null) return null;
        return ReadAction.compute(() -> cutPkg.getQualifiedName().replace('.', '/'));
    }

    public static VirtualFile findInProjectAndLibraries(Project project, String relativePath) {
        // 1. Search in content + source roots
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
            VirtualFile vf = VfsUtilCore.findRelativeFile(relativePath, root);
            if (vf != null) return vf;
        }

        // 2. Search in libraries (class roots, including JARs)
        for (VirtualFile root : ProjectRootManager.getInstance(project).orderEntries().classes().getRoots()) {
            VirtualFile vf = VfsUtilCore.findRelativeFile(relativePath, root);
            if (vf != null) return vf;
        }

        return null;
    }

    public static String getSourceCodeOfContextClasses(Project project, String relativePathOrFqcn) {
        if (relativePathOrFqcn == null || relativePathOrFqcn.isBlank()) {
            return "";
        }

        String normPath = relativePathOrFqcn.replace("\\", "/");

        // --- Try existing project/library search ---
        VirtualFile vf = findInProjectAndLibraries(project, normPath);
        if (vf != null) {
            PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(vf));
            if (psiFile != null && psiFile.isValid()) {
                return ReadAction.compute(psiFile::getText);
            }
        }

        // --- Fallback: try resolving as fully qualified class name ---
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        PsiClass psiClass = ReadAction.compute(() -> psiFacade.findClass(relativePathOrFqcn.replace("/", ".").replace(".java", ""), scope));
        if (psiClass != null && psiClass.isValid()) {
            PsiFile psiFile = ReadAction.compute(psiClass::getContainingFile);
            if (psiFile != null && psiFile.isValid()) {
                return ReadAction.compute(psiFile::getText);
            }
        }

        return "";
    }


    public static String stripCommentsAndMethodBodies(Project project, String relativePathOrFqcn) {
        if (relativePathOrFqcn == null || relativePathOrFqcn.isBlank()) return "";

        // --- Reuse existing utility to get raw source code text ---
        String sourceText = getSourceCodeOfContextClasses(project, relativePathOrFqcn);
        if (sourceText.isBlank()) return "";

        // --- Create temporary PSI file from text ---
        PsiJavaFile psiFile = (PsiJavaFile) PsiFileFactory.getInstance(project)
                .createFileFromText(
                        Paths.get(relativePathOrFqcn).getFileName().toString(), // fallback name
                        JavaFileType.INSTANCE,
                        sourceText
                );

        // --- Remove all comments (Javadoc, line, block) ---
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class)) {
            comment.delete();
        }

        // --- Replace all method/constructor bodies with "{}" ---
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)) {
            PsiCodeBlock body = method.getBody();
            if (body != null) {
                body.replace(factory.createCodeBlock());
            }
        }

        // --- Replace all static/instance initializer bodies with "{}" ---
        for (PsiClassInitializer initializer : PsiTreeUtil.findChildrenOfType(psiFile, PsiClassInitializer.class)) {
            PsiCodeBlock body = initializer.getBody();
            if (body != null) {
                body.replace(factory.createCodeBlock());
            }
        }

        return psiFile.getText();
    }


    /** Recursively find or create nested sub-directories like {@code org/example/service}. */
    public static @Nullable PsiDirectory getOrCreateSubdirectoryPath(Project project,
                                                                      PsiDirectory root,
                                                                      String relativePath) throws Exception {
        try {
            return WriteCommandAction.writeCommandAction(project).compute(() -> {
                PsiDirectory current = root;
                for (String part : relativePath.split("/")) {
                    PsiDirectory next = current.findSubdirectory(part);
                    if (next == null) {
                        next = current.createSubdirectory(part);
                    }
                    current = next;
                }
                return current;
            });
        } catch (Exception e) {
            throw new Exception("Incorrect tests source directory");
        }
    }
}
