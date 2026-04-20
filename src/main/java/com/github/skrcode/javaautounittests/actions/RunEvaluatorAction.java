package com.github.skrcode.javaautounittests.actions;

import com.github.skrcode.javaautounittests.dto.ClassTestReportRow;
import com.github.skrcode.javaautounittests.view.report.ReportView;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RunEvaluatorAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ReportView view = ReportView.getInstance(project);
        List<ClassTestReportRow> rows = view.getSelectedRows();
        if (rows.isEmpty()) rows = view.getLastRows();

        runTests(project, rows);
    }

    public static void runTests(Project project, List<ClassTestReportRow> rows) {
        if (project == null || rows == null || rows.isEmpty()) return;
        for (ClassTestReportRow row : rows) {
            if (row.testFqn() == null) continue;
            runTestClass(project, row.testFqn());
        }
    }

    static void runTestClass(Project project, String testFqn) {
        ReportView view = ReportView.getInstance(project);

        // Mark running without rebuilding the entire report.
        var state = view.getState().getOrCreate(testFqn);
        state.failureCount = 0;
        view.updateExecutionResult(testFqn, 0);

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

    private static RunnerAndConfigurationSettings createJUnitConfig(
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

    private static void attachResultListener(Project project, String testFqn) {
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(
                ExecutionManager.EXECUTION_TOPIC,
                new ExecutionListener() {
                    @Override
                    public void processTerminated(
                            @NotNull String executorId,
                            @NotNull ExecutionEnvironment env,
                            @NotNull ProcessHandler handler,
                            int exitCode
                    ) {
                        if (!(env.getRunProfile() instanceof JUnitConfiguration cfg)) {
                            return;
                        }
                        String runMainClass = cfg.getPersistentData().getMainClassName();
                        if (runMainClass == null || !runMainClass.equals(testFqn)) {
                            return;
                        }

                        ReportView view = ReportView.getInstance(project);
                        var st = view.getState().getOrCreate(testFqn);
                        st.failureCount = exitCode == 0 ? 0 : 1;
                        view.updateExecutionResult(testFqn, st.failureCount);
                        connection.disconnect();
                    }
                }
        );

    }
}
