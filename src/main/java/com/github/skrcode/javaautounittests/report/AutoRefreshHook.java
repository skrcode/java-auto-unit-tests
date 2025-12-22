package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiManager;

import java.util.concurrent.*;

public final class AutoRefreshHook implements Disposable {
    private final Project project;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pending;

    public AutoRefreshHook(Project project) {
        this.project = project;

        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override public void childrenChanged(PsiTreeChangeEvent event) { schedule(); }
            @Override public void childAdded(PsiTreeChangeEvent event) { schedule(); }
            @Override public void childRemoved(PsiTreeChangeEvent event) { schedule(); }
            @Override public void childReplaced(PsiTreeChangeEvent event) { schedule(); }
        }, this);
    }

    private void schedule() {
        JaipilotReportService svc = JaipilotReportService.getInstance(project);
        if (!svc.isAutoRefreshEnabled()) return;

        if (pending != null) pending.cancel(false);

        pending = scheduler.schedule(() -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                JaipilotReportService.getInstance(project).refreshAsync("psi_change");
            });
        }, 600, TimeUnit.MILLISECONDS);
    }

    @Override
    public void dispose() {
        scheduler.shutdownNow();
    }
}
