package com.github.skrcode.javaautounittests;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile;
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Compiles a JUnit test class, runs it with coverage, and returns:
 *   • ""  → everything succeeded
 *   • compilation errors if compilation failed
 *   • console output if test execution failed (non-zero exit or test failures)
 */
public class BuilderUtil {

    private BuilderUtil() {}

    public static String compileJUnitClass(Project project, Ref<PsiFile> testFile)  {

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();
        VirtualFile file = testFile.get().getVirtualFile();

        ApplicationManager.getApplication().invokeAndWait(() -> {
            CompilerManager.getInstance(project).compile(new VirtualFile[]{file}, (aborted, errors, warnings, context) -> {
                if (aborted) {
                    result.append("COMPILATION_ABORTED");
                } else if (errors > 0) {
                    result.append("COMPILATION_FAILED\n");
                    for (CompilerMessage msg : context.getMessages(CompilerMessageCategory.ERROR)) {
                        int line = ((CompilerMessageImpl) msg).getLine();
                        String codeLine = (line > 0) ? getLineFromVirtualFile(project, msg.getVirtualFile(), line) : "<unknown>";
                        result.append("Error at line :" + codeLine + "\n"+msg.getMessage()).append("\n\n");
                    }
                }
                latch.countDown();
            });
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                result.append("COMPILATION_TIMEOUT");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.append("COMPILATION_INTERRUPTED");
        }

        return result.toString().trim();
    }

    private static String getLineFromVirtualFile(Project project, VirtualFile file, int lineNumber) {
        if (lineNumber < 1) return "<invalid line number>";

        Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null) return "<document not found>";

        int lineIndex = lineNumber - 1;
        if (lineIndex >= doc.getLineCount()) return "<line number out of bounds>";

        int startOffset = doc.getLineStartOffset(lineIndex);
        int endOffset = doc.getLineEndOffset(lineIndex);

        return doc.getText(new TextRange(startOffset, endOffset)).trim();
    }

    public static void write(Project project,
                             Ref<PsiFile> testFile,
                             PsiDirectory packageDir,
                             String testFileName,
                             String testSourceUnifiedDiff) {

        try {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    /* --------------------------------------------------
                     * 1. Load current file content (or start with empty)
                     * -------------------------------------------------- */
                    PsiFile existingFile = testFile.get();
                    List<String> originalLines;
                    if (existingFile != null && existingFile.isValid()) {
                        Document doc = PsiDocumentManager.getInstance(project).getDocument(existingFile);
                        if (doc == null) return;                       // cannot proceed
                        originalLines = Arrays.asList(doc.getText().split("\n", -1));
                    } else {
                        originalLines = Collections.emptyList();       // new file
                    }

                    /* ------------------------------
                     * 2. Parse unified‑diff string
                     * ------------------------------ */
                    InputStream diffStream =
                            new ByteArrayInputStream(testSourceUnifiedDiff.getBytes(StandardCharsets.UTF_8));
                    UnifiedDiff unifiedDiff = UnifiedDiffReader.parseUnifiedDiff(diffStream);

                    List<UnifiedDiffFile> diffFiles = unifiedDiff.getFiles();
                    if (diffFiles.isEmpty()) throw new IllegalStateException("No patch files found");

// take the first (and only) file‑diff block
                    Patch<String> patch = diffFiles.get(0).getPatch();   // <-- this replaces getPatches()

                    /* --------------------------
                     * 3. Apply patch in memory
                     * -------------------------- */
                    List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                    String updatedContent = String.join("\n", patchedLines);

                    /* -----------------------------------
                     * 4. Persist patched content to disk
                     * ----------------------------------- */
                    PsiFile fileToProcess;
                    if (existingFile != null && existingFile.isValid()) {
                        Document doc = PsiDocumentManager.getInstance(project).getDocument(existingFile);
                        if (doc == null) return;
                        doc.setText(updatedContent);
                        PsiDocumentManager.getInstance(project).commitDocument(doc);
                        fileToProcess = existingFile;
                    } else {
                        PsiFile newFile = PsiFileFactory.getInstance(project)
                                .createFileFromText(testFileName, JavaFileType.INSTANCE, updatedContent);
                        fileToProcess = (PsiFile) packageDir.add(newFile);
                        testFile.set(fileToProcess);
                    }

                    /* -----------------------
                     * 5. Post‑process (PSI)
                     * ----------------------- */
                    JavaCodeStyleManager.getInstance(project).optimizeImports(fileToProcess);
                    new ReformatCodeProcessor(project, fileToProcess, null, false).run();
                    CodeStyleManager.getInstance(project).reformat(fileToProcess);

                } catch (Exception e) {
                    throw new RuntimeException("Failed to apply patch: " + e.getMessage(), e);
                }
            });
        }
        catch (Exception e) {

        }
    }


}
