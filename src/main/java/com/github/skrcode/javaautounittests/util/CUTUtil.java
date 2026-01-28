// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.FileInfo;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
        if (original == null) {
            return "<no file>";
        }

        PsiJavaFile scratch = ReadAction.compute(() -> (PsiJavaFile) original.copy());

        runWriteCommand(project, () -> {
            expandAll(project, scratch);          // ← your expander from earlier
            return null;
        });

        return ReadAction.compute(scratch::getText);
    }

    public static PsiDirectory resolveTestPackageDir(Project project, PsiClass cut) {
        PsiDirectory existingDir = ReadAction.compute(() -> {
            PsiClass existingTest = findExistingTestClass(cut);
            if (existingTest == null) return null;
            PsiFile file = existingTest.getContainingFile();
            return file != null ? file.getContainingDirectory() : null;
        });
        if (existingDir != null) {
            return existingDir;
        }

        VirtualFile cutFile = ReadAction.compute(() -> {
            PsiFile file = cut.getContainingFile();
            return file != null ? file.getVirtualFile() : null;
        });
        if (cutFile == null) {
            throw new IllegalStateException("Cannot locate CUT virtual file");
        }

        @Nullable Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForFile(cutFile, project));
        if (module == null) {
            throw new IllegalStateException("Cannot resolve module for CUT");
        }
        VirtualFile testRoot = resolveOrCreateTestRoot(module, cutFile);
        PsiDirectory testRootDir = ReadAction.compute(() -> PsiManager.getInstance(project).findDirectory(testRoot));
        String relPath = ReadAction.compute(() -> getTestRelativePath(cut));
        return getOrCreateSubdirectoryPath(project, testRootDir, relPath);
    }

    private static VirtualFile resolveOrCreateTestRoot(Module module, VirtualFile cutFile) {

        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

        ProjectFileIndex projectFileIndex =
                ProjectRootManager.getInstance(module.getProject()).getFileIndex();

        VirtualFile cutContentRoot = ReadAction.compute(() ->
                projectFileIndex.getContentRootForFile(cutFile));

        if (cutContentRoot == null) {
            throw new IllegalStateException("No content root found for CUT");
        }

        // 2. Get existing test source roots
        List<VirtualFile> testRoots = ReadAction.compute(() ->
                rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE));

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

        VirtualFile sourceRoot = ReadAction.compute(() -> projectFileIndex.getSourceRootForFile(cutFile));

        // 3b. Try to reuse or create a sibling test root inferred from the CUT source root
        List<String> candidateTestRootPaths = deriveCandidateTestRootPaths(sourceRoot, cutContentRoot);
        VirtualFile sibling = tryReuseCandidateTestRoot(module, candidateTestRootPaths, cutContentRoot);
        if (sibling != null) {
            return sibling;
        }
        VirtualFile createdSibling = tryCreateCandidateTestRoot(module, candidateTestRootPaths, cutContentRoot);
        if (createdSibling != null) {
            return createdSibling;
        }

        // 4. No test root exists → create src/test/java deterministically
        VirtualFile preferredContentRoot = pickPreferredTestContentRoot(module, cutContentRoot, cutFile);

        return runWrite(module.getProject(), () -> {
            ModifiableRootModel model = rootManager.getModifiableModel();
            try {
                ContentEntry entry = findOrCreateContentEntry(model, preferredContentRoot);

                // Canonical test root path
                VirtualFile testRoot =
                        createDirectoryIfMissingSafe(preferredContentRoot, "src/test/java");

                entry.addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE);

                model.commit();
                return testRoot;
            } catch (Exception e) {
                model.dispose();
                throw new RuntimeException("Failed to create test source root", e);
            }
        });
    }

    private static ContentEntry findOrCreateContentEntry(ModifiableRootModel model, VirtualFile preferredContentRoot) {
        return Arrays.stream(model.getContentEntries())
                .filter(e -> e.getFile() != null && e.getFile().equals(preferredContentRoot))
                .findFirst()
                .orElseGet(() -> model.addContentEntry(preferredContentRoot));
    }

    private static VirtualFile ensureRegisteredTestRoot(Module module, VirtualFile testRoot) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        if (Arrays.asList(rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE)).contains(testRoot)) {
            return testRoot;
        }
        return runWrite(module.getProject(), () -> {
            ModifiableRootModel model = rootManager.getModifiableModel();
            try {
                VirtualFile contentRoot = findContentRootFor(module, testRoot);
                ContentEntry entry = findOrCreateContentEntry(model, contentRoot);
                entry.addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE);
                model.commit();
                return testRoot;
            } catch (Exception e) {
                model.dispose();
                throw new RuntimeException("Failed to register test source root", e);
            }
        });
    }

    private static VirtualFile findContentRootFor(Module module, VirtualFile file) {
        ProjectFileIndex index = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
        VirtualFile root = index.getContentRootForFile(file);
        if (root != null) {
            return root;
        }
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        if (roots.length > 0) {
            return roots[0];
        }
        return file;
    }

    private static VirtualFile tryReuseCandidateTestRoot(Module module,
                                                         List<String> candidatePaths,
                                                         VirtualFile cutContentRoot) {
        LocalFileSystem lfs = LocalFileSystem.getInstance();
        for (String path : candidatePaths) {
            if (!pathStartsWithRoot(path, cutContentRoot)) continue;
            VirtualFile vf = lfs.findFileByPath(path);
            if (vf != null && isAcceptableTestRoot(module, vf)) {
                return ensureRegisteredTestRoot(module, vf);
            }
        }
        return null;
    }

    private static VirtualFile tryCreateCandidateTestRoot(Module module,
                                                          List<String> candidatePaths,
                                                          VirtualFile cutContentRoot) {
        for (String path : candidatePaths) {
            if (!pathStartsWithRoot(path, cutContentRoot)) continue;
            VirtualFile vf = runWrite(module.getProject(), () -> createDirectoryIfMissingSafe(path));
            if (vf != null && isAcceptableTestRoot(module, vf)) {
                return ensureRegisteredTestRoot(module, vf);
            }
        }
        return null;
    }

    private static boolean pathStartsWithRoot(String path, VirtualFile root) {
        if (root == null) return false;
        String normRoot = root.getPath().replace('\\', '/');
        String normPath = path.replace('\\', '/');
        return normPath.startsWith(normRoot);
    }

    private static VirtualFile pickPreferredTestContentRoot(Module module,
                                                            VirtualFile cutContentRoot,
                                                            VirtualFile cutFile) {
        ProjectFileIndex index = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
        ModuleRootManager roots = ModuleRootManager.getInstance(module);

        VirtualFile moduleDir = toVirtualFile(ModuleUtilCore.getModuleDirPath(module));
        if (isAcceptableContentRoot(module, moduleDir)) {
            return moduleDir;
        }

        VirtualFile derivedModuleRoot = deriveRootAboveSrc(index, cutFile);
        if (isAcceptableContentRoot(module, derivedModuleRoot)) {
            return derivedModuleRoot;
        }

        if (isAcceptableContentRoot(module, cutContentRoot)) {
            return cutContentRoot;
        }

        return Arrays.stream(roots.getContentRoots())
                .filter(root -> isAcceptableContentRoot(module, root))
                .sorted(Comparator.comparing(VirtualFile::getPath))
                .findFirst()
                .orElse(cutContentRoot);
    }

    private static boolean isAcceptableContentRoot(Module module, @Nullable VirtualFile root) {
        if (root == null || !root.isValid() || isIdeaDirectory(root)) {
            return false;
        }
        ProjectFileIndex index = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
        if (index.isExcluded(root)) {
            return false;
        }
        // Accept if it is a registered content root, or inside project content.
        if (Arrays.stream(ModuleRootManager.getInstance(module).getContentRoots()).anyMatch(root::equals)) {
            return true;
        }
        return index.isInContent(root);
    }

    private static boolean isAcceptableTestRoot(Module module, @Nullable VirtualFile root) {
        if (root == null || !root.isValid() || isIdeaDirectory(root)) {
            return false;
        }
        ProjectFileIndex index = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
        return index.isInContent(root) && !index.isExcluded(root);
    }

    private static @Nullable VirtualFile deriveRootAboveSrc(ProjectFileIndex index, @Nullable VirtualFile cutFile) {
        if (cutFile == null) return null;
        VirtualFile sourceRoot = ReadAction.compute(() -> index.getSourceRootForFile(cutFile));
        if (sourceRoot == null) return null;

        VirtualFile srcDir = findAncestorNamed(sourceRoot, "src");
        if (srcDir != null && srcDir.getParent() != null) {
            return srcDir.getParent();
        }
        return null;
    }

    private static @Nullable VirtualFile findAncestorNamed(VirtualFile start, String name) {
        VirtualFile current = start;
        while (current != null && !name.equals(current.getName())) {
            current = current.getParent();
        }
        return current;
    }

    private static boolean isIdeaDirectory(VirtualFile root) {
        String path = root.getPath().replace('\\', '/');
        return path.endsWith("/.idea") || path.contains("/.idea/");
    }

    private static List<String> deriveCandidateTestRootPaths(@Nullable VirtualFile sourceRoot,
                                                             @Nullable VirtualFile contentRoot) {
        if (sourceRoot == null || contentRoot == null) return List.of();
        String sourcePath = sourceRoot.getPath().replace('\\', '/');
        String contentPath = contentRoot.getPath().replace('\\', '/');
        if (!sourcePath.startsWith(contentPath)) return List.of();

        Set<String> candidates = new LinkedHashSet<>();

        // Replace /src/<variant>/... with /src/test/...
        int srcIdx = sourcePath.indexOf("/src/");
        if (srcIdx >= 0) {
            String afterSrc = sourcePath.substring(srcIdx + "/src/".length()); // e.g., main/java
            int slash = afterSrc.indexOf('/');
            if (slash >= 0) {
                String remainder = afterSrc.substring(slash + 1); // e.g., java
                String prefix = sourcePath.substring(0, srcIdx);   // before /src
                candidates.add(prefix + "/src/test/" + remainder);
            } else {
                String prefix = sourcePath.substring(0, srcIdx);
                candidates.add(prefix + "/src/test");
            }
        }

        // Common fallbacks
        if (sourcePath.contains("/src/main/java")) {
            candidates.add(sourcePath.replace("/src/main/java", "/src/test/java"));
        }
        if (sourcePath.contains("/src/main/kotlin")) {
            candidates.add(sourcePath.replace("/src/main/kotlin", "/src/test/kotlin"));
        }
        if (sourcePath.endsWith("/src/java")) {
            candidates.add(sourcePath.replace("/src/java", "/src/test/java"));
        }

        return new ArrayList<>(candidates);
    }

    private static @Nullable VirtualFile toVirtualFile(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(path);
    }

    private static VirtualFile createDirectoryIfMissingSafe(String path) {
        try {
            return VfsUtil.createDirectoryIfMissing(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }

    private static VirtualFile createDirectoryIfMissingSafe(VirtualFile parent, String relative) {
        try {
            return VfsUtil.createDirectoryIfMissing(parent, relative);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create directory: " + parent.getPath() + "/" + relative, e);
        }
    }

    private static <T> T runWrite(Project project, Computable<T> action) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return ApplicationManager.getApplication().runWriteAction(action);
        }
        Ref<T> result = Ref.create();
        ApplicationManager.getApplication().invokeAndWait(
                () -> result.set(ApplicationManager.getApplication().runWriteAction(action))
        );
        return result.get();
    }

    private static <T> T runWriteCommand(Project project, Computable<T> action) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return WriteCommandAction.runWriteCommandAction(project, action);
        }
        Ref<T> result = Ref.create();
        ApplicationManager.getApplication().invokeAndWait(
                () -> result.set(WriteCommandAction.runWriteCommandAction(project, action))
        );
        return result.get();
    }


    @Nullable
    private static PsiClass findExistingTestClass(PsiClass cut) {
        return ReadAction.compute(() -> {
            if (cut == null || !cut.isValid()) return null;
            for (PsiElement test : TestFinderHelper.findTestsForClass(cut)) {
                if (test instanceof PsiClass psiClass && psiClass.isValid()) {
                    return psiClass;
                }
            }
            return null;
        });
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

        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
            return ReadAction.compute(() -> getSourceCodeOfContextClasses(project, relativePathOrFqcn));
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

        AtomicReference<String> result = new AtomicReference<>("");

        ApplicationManager.getApplication().invokeAndWait(() ->
                ApplicationManager.getApplication().runWriteAction(() -> {
                    String sourceText = getSourceCodeOfContextClasses(project, relativePathOrFqcn);
                    if (sourceText.isBlank()) {
                        result.set("");
                        return;
                    }

                    PsiJavaFile psiFile = (PsiJavaFile) PsiFileFactory.getInstance(project)
                            .createFileFromText(
                                    Paths.get(relativePathOrFqcn).getFileName().toString(), // fallback name
                                    JavaFileType.INSTANCE,
                                    sourceText
                            );

                    for (PsiComment comment : PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class)) {
                        comment.delete();
                    }

                    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
                    for (PsiMethod method : PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)) {
                        PsiCodeBlock body = method.getBody();
                        if (body != null) {
                            body.replace(factory.createCodeBlock());
                        }
                    }

                    for (PsiClassInitializer initializer : PsiTreeUtil.findChildrenOfType(psiFile, PsiClassInitializer.class)) {
                        PsiCodeBlock body = initializer.getBody();
                        if (body != null) {
                            body.replace(factory.createCodeBlock());
                        }
                    }

                    result.set(psiFile.getText());
                })
        );

        return result.get();
    }


    /** Recursively find or create nested sub-directories like {@code org/example/service}. */
    private static @Nullable PsiDirectory getOrCreateSubdirectoryPath(Project project,
                                                                      PsiDirectory root,
                                                                      String relativePath) {
        return runWriteCommand(project, () -> {
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
                return null;
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

        return runWriteCommand(project, () -> {
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
