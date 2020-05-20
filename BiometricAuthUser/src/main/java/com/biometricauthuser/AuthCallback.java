package com.biometricauthuser;

public interface AuthCallback {
    void onSdkVersionNotSupported();

    void onAuthenticationPermissionNotGranted();

    void onAuthenticationInternalError(String error);

    void onAuthenticationFailed();

    void onAuthenticationSuccessful();

    void onAuthenticationError(int errorCode, CharSequence errString);
}
