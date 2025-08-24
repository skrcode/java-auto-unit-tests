package com.github.skrcode.javaautounittests;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Compiles a JUnit test class, runs it with coverage, and returns:
 *   • ""  → everything succeeded
 *   • compilation errors if compilation failed
 *   • console output if test execution failed (non-zero exit or test failures)
 */
public class BuilderUtil {

    private BuilderUtil() {}

    public static String compileJUnitClass(Project project, Ref<PsiFile> testFile)  {

        String staticErrors = getAllErrorMessages(project, testFile.get());
        if(!staticErrors.isEmpty()) return staticErrors;

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

    public static String getAllErrorMessages(Project project, PsiFile psiFile) {
        Document doc = psiFile.getViewProvider().getDocument();

        // Collect all highlights
        List<HighlightInfo> infos = com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
                .getHighlights(doc, null, project);

        // Filter and join as a string
        return infos.stream()
                .filter(info -> info.getSeverity().equals(com.intellij.lang.annotation.HighlightSeverity.ERROR))
                .map(info -> {
                    int line = doc.getLineNumber(info.getStartOffset()) + 1;
                    return "Line " + line + ": " + info.getDescription();
                })
                .collect(Collectors.joining("\n"));
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
                             String testSource) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiFile existingFile = testFile.get();
            PsiFile fileToProcess;

            if (existingFile != null && existingFile.isValid()) {
                Document doc = PsiDocumentManager.getInstance(project).getDocument(existingFile);
                if (doc != null) {
                    doc.setText(testSource);
                    PsiDocumentManager.getInstance(project).commitDocument(doc);
                }
                fileToProcess = existingFile;
            } else {
                PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(
                        testFileName, JavaFileType.INSTANCE, testSource);
                PsiFile addedFile = (PsiFile) packageDir.add(newFile);
                testFile.set(addedFile);
                fileToProcess = addedFile;
            }

            // ✅ Optimize imports
            JavaCodeStyleManager.getInstance(project).optimizeImports(fileToProcess);

            // ✅ Rearrange entries
//            CodeStyleManager.getInstance(project).(fileToProcess);
            new ReformatCodeProcessor(project, fileToProcess, null, false).run();

            // ✅ Cleanup code (reformat)
            CodeStyleManager.getInstance(project).reformat(fileToProcess);
        });
    }


}
