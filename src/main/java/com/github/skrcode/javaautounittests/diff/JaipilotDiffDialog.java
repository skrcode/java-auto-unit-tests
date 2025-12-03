package com.github.skrcode.javaautounittests.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class JaipilotDiffDialog extends DialogWrapper {

    private final Project project;
    private final PsiFile psiFile;
    private final JaipilotDiffResult diffResult;

    private final Map<JaipilotHunk, JCheckBox> selectedMap = new LinkedHashMap<>();

    private JPanel root;

    private DiffRequestPanel diffPanel;
    private DiffContentFactory contentFactory;
    private FileType fileType;

    public JaipilotDiffDialog(
            @NotNull Project project,
            @NotNull PsiFile psiFile,
            @NotNull JaipilotDiffResult diffResult
    ) {
        super(project, true);
        this.project = project;
        this.psiFile = psiFile;
        this.diffResult = diffResult;
        setTitle("JAIPilot – Review and Apply Changes");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {

        contentFactory = DiffContentFactory.getInstance();
        fileType = psiFile.getFileType();

        // Create diff panel ONCE (critical for no flicker)
        diffPanel = DiffManager.getInstance()
                .createRequestPanel(project, getDisposable(), null);

        // Initial content
        diffPanel.setRequest(buildDiffRequest());

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                diffPanel.getComponent(),
                createHunksPanel()
        );
        split.setResizeWeight(0.75);

        root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1600, 900));

        return root;
    }

    // ---------------------------------------------------------------------
    // DIFF REQUEST BUILDER (left original, right preview)
    // ---------------------------------------------------------------------

    private SimpleDiffRequest buildDiffRequest() {

        String original = diffResult.originalSource;
        String preview = buildPreviewText();

        var leftContent = contentFactory.create(original, fileType);
        var rightContent = contentFactory.create(preview, fileType);

        return new SimpleDiffRequest(
                "Preview",
                leftContent,
                rightContent,
                "Original",
                "Preview"
        );
    }

    // ---------------------------------------------------------------------
    // HUNKS SIDEBAR
    // ---------------------------------------------------------------------

    private JComponent createHunksPanel() {

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        List<JaipilotHunk> hunks = diffResult.hunks != null ? diffResult.hunks : List.of();

        if (hunks.isEmpty()) {
            p.add(new JLabel("No changes detected."));
        } else {
            int i = 1;
            for (JaipilotHunk h : hunks) {
                p.add(createHunkBox(h, i++));
                p.add(Box.createVerticalStrut(8));
            }
        }

        JScrollPane scroll = new JScrollPane(p);
        scroll.setPreferredSize(new Dimension(360, 900));
        return scroll;
    }

    private JComponent createHunkBox(JaipilotHunk hunk, int index) {

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel title = new JLabel("Change #" + index);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox cb = new JCheckBox("Apply this change", true);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectedMap.put(hunk, cb);

        // Instant preview update with zero flicker
        cb.addActionListener(e -> diffPanel.setRequest(buildDiffRequest()));

        JTextArea reason = new JTextArea(hunk.reason != null ? hunk.reason : "");
        reason.setEditable(false);
        reason.setOpaque(false);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        reason.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.add(title);
        box.add(Box.createVerticalStrut(5));
        box.add(cb);
        box.add(Box.createVerticalStrut(8));
        box.add(reason);

        return box;
    }

    // ---------------------------------------------------------------------
    // BUILD PREVIEW TEXT USING SELECTED HUNKS
    // ---------------------------------------------------------------------

    private String buildPreviewText() {

        String original = diffResult.originalSource;
        List<JaipilotHunk> hunks = diffResult.hunks != null
                ? new ArrayList<>(diffResult.hunks)
                : new ArrayList<>();

        hunks.sort(Comparator.comparingInt(h -> h.startOffset));

        StringBuilder sb = new StringBuilder();
        int cursor = 0;

        for (JaipilotHunk h : hunks) {

            if (h.startOffset < cursor)
                continue;

            sb.append(original, cursor, h.startOffset);

            boolean apply = selectedMap.getOrDefault(h, new JCheckBox()).isSelected();

            if (apply) {
                sb.append(h.afterText != null ? h.afterText : "");
            } else {
                sb.append(original, h.startOffset, h.endOffset);
            }

            cursor = h.endOffset;
        }

        if (cursor < original.length()) {
            sb.append(original.substring(cursor));
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // APPLY BUTTON
    // ---------------------------------------------------------------------

    @Override
    protected @NotNull Action[] createActions() {
        Action ok = getOKAction();
        ok.putValue(Action.NAME, "Apply Selected Changes");
        return new Action[]{ok, getCancelAction()};
    }

    @Override
    protected void doOKAction() {

        String finalSource = buildPreviewText();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            var doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            if (doc != null) {
                doc.setText(finalSource);
                PsiDocumentManager.getInstance(project).commitDocument(doc);
            }
        });

        super.doOKAction();
    }
}
