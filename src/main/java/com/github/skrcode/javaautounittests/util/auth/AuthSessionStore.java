package com.github.skrcode.javaautounittests.util.auth;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.text.StringUtil;

public final class AuthSessionStore {
    private static final CredentialAttributes ACCESS_TOKEN_ATTR =
            new CredentialAttributes(CredentialAttributesKt.generateServiceName("JAIPilot", "supabase_access_token"));
    private static final CredentialAttributes REFRESH_TOKEN_ATTR =
            new CredentialAttributes(CredentialAttributesKt.generateServiceName("JAIPilot", "supabase_refresh_token"));

    private AuthSessionStore() {}

    public static void saveTokens(String accessToken, String refreshToken) {
        PasswordSafe.getInstance().set(ACCESS_TOKEN_ATTR, new Credentials("jaipilot", StringUtil.notNullize(accessToken)));
        PasswordSafe.getInstance().set(REFRESH_TOKEN_ATTR, new Credentials("jaipilot", StringUtil.notNullize(refreshToken)));
    }

    public static String getAccessToken() {
        Credentials credentials = PasswordSafe.getInstance().get(ACCESS_TOKEN_ATTR);
        return credentials == null ? "" : StringUtil.notNullize(credentials.getPasswordAsString());
    }

    public static String getRefreshToken() {
        Credentials credentials = PasswordSafe.getInstance().get(REFRESH_TOKEN_ATTR);
        return credentials == null ? "" : StringUtil.notNullize(credentials.getPasswordAsString());
    }

    public static void clear() {
        PasswordSafe.getInstance().set(ACCESS_TOKEN_ATTR, null);
        PasswordSafe.getInstance().set(REFRESH_TOKEN_ATTR, null);
    }
}
