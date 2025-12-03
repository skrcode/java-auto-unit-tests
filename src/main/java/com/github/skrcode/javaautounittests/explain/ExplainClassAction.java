package com.github.skrcode.javaautounittests.explain;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class ExplainClassAction extends AnAction {

    public ExplainClassAction() {
        super("Explain This Class",
                "Explain what this class does",
                AllIcons.Actions.Help);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile javaFile)) return;

        String source = javaFile.getText();

        // 1. Call your LLM
        String explanation = callLLMExplain(source);

        // 2. Get the tool window
        ToolWindow tw = ToolWindowManager.getInstance(project)
                .getToolWindow(ExplainToolWindowFactory.TOOLWINDOW_ID);

        if (tw == null) return;
        tw.activate(null);

        // 3. Find the panel and show the explanation
        ExplainToolWindowPanel panel = (ExplainToolWindowPanel)
                tw.getContentManager().getContent(0).getComponent();

        panel.showText(explanation);
    }

    private String callLLMExplain(String source) {
        // Replace with your Edge Function / Gemini / backend call
        return """
               # Class Summary
               This class processes discount logic.

               ## What it does
               • Validates price  
               • Applies discount  
               • Handles edge cases  
               
               ## Methods
               - calculate(): returns final price after applying discount.
               
               ## Notes
               • Consider adding validation for negative discounts.  
               • Consider documenting expected ranges.  
               """;
    }
}
