package com.github.skrcode.javaautounittests.dto;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

public record FileInfo(
        @Nullable PsiClass cutClass,
        @Nullable PsiJavaFile psiFile,
        @Nullable String simpleName,
        @Nullable String qualifiedName,
        @Nullable String filePath,
        @Nullable String source
) {}

