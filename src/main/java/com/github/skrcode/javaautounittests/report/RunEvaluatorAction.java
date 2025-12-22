package com.github.skrcode.javaautounittests.report;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RunEvaluatorAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        JaipilotReportPanel panel =
                JaipilotReportPanel.getActiveInstance(project);
        if (panel == null) return;

        List<ClassTestReportRow> rows = panel.getSelectedRows();
        if (rows.isEmpty()) return;

        for (ClassTestReportRow row : rows) {
            if (row.getTestFqn() == null) continue;
            runTestClass(project, row.getTestFqn());
        }
    }

    private void runTestClass(Project project, String testFqn) {
        JaipilotReportService service =
                JaipilotReportService.getInstance(project);

        // mark RUNNING
        var state = service.getState().getOrCreate(testFqn);
        state.status = TestRunStatus.RUNNING;
        state.failureCount = 0;
        service.refreshAsync("test_running");

        PsiClass testPsi =
                JavaPsiFacade.getInstance(project)
                        .findClass(testFqn, GlobalSearchScope.projectScope(project));

        if (testPsi == null) return;

        RunnerAndConfigurationSettings settings =
                createJUnitConfig(project, testPsi);
        if (settings == null) return;

        attachResultListener(project, testFqn);

        ProgramRunnerUtil.executeConfiguration(
                settings,
                com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
        );
    }

    private RunnerAndConfigurationSettings createJUnitConfig(
            Project project,
            PsiClass testPsi
    ) {
        RunManager runManager = RunManager.getInstance(project);
        ConfigurationFactory factory =
                JUnitConfigurationType.getInstance().getConfigurationFactories()[0];

        RunnerAndConfigurationSettings settings =
                runManager.createConfiguration(
                        "JAIPilot Evaluator: " + testPsi.getName(),
                        factory
                );

        JUnitConfiguration cfg = (JUnitConfiguration) settings.getConfiguration();
        JUnitConfiguration.Data data = cfg.getPersistentData();
        data.TEST_OBJECT = JUnitConfiguration.TEST_CLASS;
        data.setMainClass(testPsi);

        settings.setTemporary(true);
        return settings;
    }

    private void attachResultListener(Project project, String testFqn) {
        project.getMessageBus().connect().subscribe(
                ExecutionManager.EXECUTION_TOPIC,
                new ExecutionListener() {
                    @Override
                    public void processTerminated(
                            @NotNull String executorId,
                            @NotNull ExecutionEnvironment env,
                            @NotNull ProcessHandler handler,
                            int exitCode
                    ) {
                        JaipilotReportService svc =
                                JaipilotReportService.getInstance(project);

                        var st = svc.getState().getOrCreate(testFqn);

                        if (exitCode == 0) {
                            st.status = TestRunStatus.PASS;
                            st.failureCount = 0;
                        } else {
                            st.status = TestRunStatus.FAIL;
                            st.failureCount = 1;
                        }

                        svc.refreshAsync("test_finished");
                    }
                }
        );

    }
}
