// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.FileInfo;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFinderHelper;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

    public static PsiDirectory resolveTestPackageDir(Project project, PsiClass cut) {
        PsiClass existingTest = findExistingTestClass(cut);
        if (existingTest != null) {
            return existingTest.getContainingFile().getContainingDirectory();
        }
        VirtualFile cutFile = cut.getContainingFile().getVirtualFile();
        @Nullable Module module = ModuleUtilCore.findModuleForFile(cutFile, project);
        if (module == null) {
            throw new IllegalStateException("Cannot resolve module for CUT");
        }
        VirtualFile testRoot = resolveOrCreateTestRoot(module, cutFile);
        PsiDirectory testRootDir = PsiManager.getInstance(project).findDirectory(testRoot);
        String relPath = getTestRelativePath(cut);
        return getOrCreateSubdirectoryPath(project, testRootDir, relPath);
    }

    private static VirtualFile resolveOrCreateTestRoot(Module module, VirtualFile cutFile) {

        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

        // 1. Find CUT content root (authoritative anchor)
        ProjectFileIndex projectFileIndex =
                ProjectRootManager.getInstance(module.getProject()).getFileIndex();

        VirtualFile cutContentRoot =
                projectFileIndex.getContentRootForFile(cutFile);

        if (cutContentRoot == null) {
            throw new IllegalStateException("No content root found for CUT");
        }

        // 2. Get existing test source roots
        List<VirtualFile> testRoots =
                rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE);

        // 3. If test roots exist, choose deterministically
        if (!testRoots.isEmpty()) {
            return testRoots.stream()
                    // Prefer test root under same content root
                    .filter(root -> VfsUtilCore.isAncestor(cutContentRoot, root, false))
                    // Deterministic ordering
                    .sorted(Comparator.comparing(VirtualFile::getPath))
                    .findFirst()
                    // Fallback: global deterministic choice
                    .orElse(
                            testRoots.stream()
                                    .sorted(Comparator.comparing(VirtualFile::getPath))
                                    .findFirst()
                                    .orElseThrow()
                    );
        }

        // 4. No test root exists → create src/test/java deterministically
        return WriteAction.compute(() -> {
            ModifiableRootModel model = rootManager.getModifiableModel();
            try {
                // Use existing content entry if present
                ContentEntry entry = Arrays.stream(model.getContentEntries())
                        .filter(e -> e.getFile() != null &&
                                e.getFile().equals(cutContentRoot))
                        .findFirst()
                        .orElseGet(() -> model.addContentEntry(cutContentRoot));

                // Canonical test root path
                VirtualFile testRoot =
                        VfsUtil.createDirectoryIfMissing(cutContentRoot, "src/test/java");

                entry.addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE);

                model.commit();
                return testRoot;
            } catch (Exception e) {
                model.dispose();
                throw new RuntimeException("Failed to create test source root", e);
            }
        });
    }


    @Nullable
    private static PsiClass findExistingTestClass(PsiClass cut) {
        for (PsiElement test : TestFinderHelper.findTestsForClass(cut)) {
            if (test.isValid()) {
                return (PsiClass) test;
            }
        }
        return null;
    }

    private static @Nullable String getTestRelativePath(PsiClass cut) {
        PsiPackage cutPkg = ReadAction.compute(() ->
                JavaDirectoryService.getInstance().getPackage(cut.getContainingFile().getContainingDirectory())
        );
        if (cutPkg == null) return null;
        return ReadAction.compute(() -> cutPkg.getQualifiedName().replace('.', '/'));
    }

    private static VirtualFile findInProjectAndLibraries(Project project, String relativePath) {
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

    private static String getSourceCodeOfContextClasses(Project project, String relativePathOrFqcn) {
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
    private static @Nullable PsiDirectory getOrCreateSubdirectoryPath(Project project,
                                                                      PsiDirectory root,
                                                                      String relativePath) {
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
    }

    public static @Nullable FileInfo getCutFileInfo(PsiClass cut) {
        if (!ReadAction.compute(cut::isValid)) {
            return null;
        }

        return ReadAction.compute(() -> {
            String name = cut.getName();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("CUT has no simple name");
            }

            String qName = cut.getQualifiedName();

            PsiJavaFile f = (PsiJavaFile) cut.getContainingFile();
            String path = null;
            String source = null;

            if (f != null) {
                PsiDirectory dir = f.getContainingDirectory();
                PsiPackage pkg = dir != null
                        ? JavaDirectoryService.getInstance().getPackage(dir)
                        : null;

                String pkgPath = pkg != null
                        ? pkg.getQualifiedName().replace('.', '/')
                        : "";

                path = pkgPath.isEmpty()
                        ? f.getName()
                        : pkgPath + "/" + f.getName();

                source = f.getText();
            }

            return new FileInfo(
                    cut,
                    f,
                    name,
                    qName,
                    path,
                    source
            );
        });
    }


    public static @Nullable FileInfo getOrCreateTestFile(
            Project project,
            FileInfo cutInfo
    ) {
        PsiClass cut = cutInfo.cutClass();

        // 1️⃣ Existing test wins (READ)
        PsiClass existingTest =
                ReadAction.compute(() -> findExistingTestClass(cut));

        if (existingTest != null && ReadAction.compute(existingTest::isValid)) {
            return ReadAction.compute(() -> {
                PsiJavaFile testFile =
                        (PsiJavaFile) existingTest.getContainingFile();

                String path = null;
                String source = null;

                if (testFile != null) {
                    PsiDirectory dir = testFile.getContainingDirectory();
                    PsiPackage pkg = dir != null
                            ? JavaDirectoryService.getInstance().getPackage(dir)
                            : null;

                    String pkgPath = pkg != null
                            ? pkg.getQualifiedName().replace('.', '/')
                            : "";

                    path = pkgPath.isEmpty()
                            ? testFile.getName()
                            : pkgPath + "/" + testFile.getName();

                    source = testFile.getText(); // ✅ READ-safe
                }

                return new FileInfo(
                        null,
                        testFile,
                        existingTest.getName(),
                        existingTest.getQualifiedName(),
                        path,
                        source
                );
            });
        }

        // 2️⃣ Create new test
        String testClassName = cutInfo.simpleName() + "Test";
        PsiDirectory testDir = resolveTestPackageDir(project, cut);

        return WriteCommandAction.writeCommandAction(project).compute(() -> {
            PsiFile file = testDir.findFile(testClassName + ".java");
            if (file == null) {
                file = testDir.createFile(testClassName + ".java");
            }

            PsiJavaFile javaFile = (PsiJavaFile) file;

            PsiPackage pkg =
                    JavaDirectoryService.getInstance().getPackage(testDir);

            if (pkg != null && javaFile.getPackageStatement() == null) {
                PsiElementFactory factory =
                        JavaPsiFacade.getInstance(project).getElementFactory();

                PsiPackageStatement stmt =
                        factory.createPackageStatement(pkg.getQualifiedName());

                javaFile.addAfter(stmt, null);
            }

            String qName = (pkg == null)
                    ? testClassName
                    : pkg.getQualifiedName() + "." + testClassName;

            String path = (pkg == null || pkg.getQualifiedName().isBlank())
                    ? javaFile.getName()
                    : pkg.getQualifiedName().replace('.', '/') + "/" + javaFile.getName();

            String source = javaFile.getText(); // newly created → safe

            return new FileInfo(
                    null,
                    javaFile,
                    testClassName,
                    qName,
                    path,
                    source
            );
        });
    }



    public static boolean testFileExists(@Nullable PsiJavaFile testFile) {
        return testFile != null && ReadAction.compute(testFile::isValid);
    }
}
