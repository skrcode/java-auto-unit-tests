package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class JaipilotReportBootstrap implements Disposable {
    private final AutoRefreshHook hook;

    public JaipilotReportBootstrap(Project project) {
        this.hook = new AutoRefreshHook(project);
    }

    @Override
    public void dispose() {
        hook.dispose();
    }
}
