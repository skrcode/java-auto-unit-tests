package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.DTOs.CacheDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class CacheJsonUtil {

    public static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static CacheDTO read(PsiFile file) {
        try {
            String text = file.getText();
            return gson.fromJson(text, CacheDTO.class);
        } catch (Exception e) {
            return new CacheDTO(); // fallback
        }
    }

    public static void write(Project project, PsiFile file, CacheDTO entry) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
            if (doc != null) {
                doc.setText(gson.toJson(entry));
            }
        });
    }
}
