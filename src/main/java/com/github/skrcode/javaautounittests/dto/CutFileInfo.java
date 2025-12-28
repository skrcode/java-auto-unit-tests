package com.github.skrcode.javaautounittests.dto;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

public record CutFileInfo(
        PsiClass cutClass,
        String simpleName,
        String qualifiedName,
        @Nullable String filePath
) {}
