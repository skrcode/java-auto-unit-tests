/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.settings.auth;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;

public class SupabaseAuthDialog extends DialogWrapper {
    private final JBCefBrowser browser;

    public SupabaseAuthDialog() {
        super(true);
        setTitle("Login with Supabase");
        browser = new JBCefBrowser("https://www.jaipilot.com/login/sign_in");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(browser.getComponent(), java.awt.BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected void dispose() {
        browser.dispose();
        super.dispose();
    }

    public static void showDialog() {
        SupabaseAuthDialog dialog = new SupabaseAuthDialog();
        dialog.show();
    }
}
