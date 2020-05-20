package com.biometricauthuser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.UUID;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

@RequiresApi(api = Build.VERSION_CODES.M)
public class AuthManager {
    private String title;
    private String subtitle;
    private String description;
    private String negativeButtonText;
    private Boolean confirmationRequired;
    private Context context;
    private boolean isKeyGenerated;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private static final String KEY_NAME = UUID.randomUUID().toString();
    private static final byte[] SECRET_BYTE_ARRAY = new byte[]{1, 2, 3, 4, 5, 6};
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;
    private static final int AUTHENTICATION_DURATION_SECONDS = 20;
    private KeyguardManager mKeyguardManager;
    private AuthCallback authCallback;

    private AuthManager(AuthMangerBuilder authMangerBuilder) {
        this.context = authMangerBuilder.context;
        this.title = authMangerBuilder.title;
        this.subtitle = authMangerBuilder.subtitle;
        this.description = authMangerBuilder.subtitle;
        this.confirmationRequired = authMangerBuilder.confirmationRequired;
        this.negativeButtonText = authMangerBuilder.negativeButtonText;
    }

    public void authenticate(AuthCallback authCallback) {
        this.authCallback = authCallback;
        if (title == null) {
            authCallback.onAuthenticationInternalError("Title cannot be null");
        }

        if (subtitle == null) {
            authCallback.onAuthenticationInternalError("Subtitle cannot be null");
        }
        if (description == null) {
            authCallback.onAuthenticationInternalError("Description cannot be null");
        }
        if (confirmationRequired == null) {
            confirmationRequired = false;
        }

        if (!AuthUtils.isSdkVersionSupported()) {
            authCallback.onSdkVersionNotSupported();
            return;
        }

        if (!AuthUtils.isPermissionGranted(context)) {
            authCallback.onAuthenticationPermissionNotGranted();
            return;
        }

        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (mKeyguardManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mKeyguardManager.isKeyguardSecure();
            }
        }

        generateKey();
        Executor executor = ContextCompat.getMainExecutor(context);
        biometricPrompt = new BiometricPrompt((FragmentActivity) context,
                executor, new BiometricAuthCallback());
        displayAuthDialog();
    }

    private void displayAuthDialog() {
        if (AuthUtils.isPromptEnabled()) {
            displayAuthPrompt();
        } else {
            displayAuthPromptV23();
        }
    }

    private void displayAuthPrompt() {
        if (confirmationRequired) {
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setConfirmationRequired(true)
                    .setDeviceCredentialAllowed(true)
                    .build();
        } else {
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setNegativeButtonText(negativeButtonText)
                    .setConfirmationRequired(true)
                    .build();
        }

        biometricPrompt.authenticate(promptInfo);
    }

    private void displayAuthPromptV23() {
        if (confirmationRequired) {
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setConfirmationRequired(true)
                    .setNegativeButtonText("Try Another Way")
                    .build();
        } else {
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setConfirmationRequired(true)
                    .setNegativeButtonText(negativeButtonText)
                    .build();
        }

        biometricPrompt.authenticate(promptInfo);
    }

    private void generateKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            generateSecretKey(new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .build());
        }
    }

    private void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) {
        KeyGenerator keyGenerator;
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setUserAuthenticationRequired(true)
                        // Require that the user has unlocked in the last 20 seconds
                        .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());
            }
            keyGenerator.generateKey();
            isKeyGenerated = true;
        } catch (NoSuchAlgorithmException | NoSuchProviderException |
                InvalidAlgorithmParameterException | CertificateException |
                KeyStoreException | IOException e) {
            e.printStackTrace();
            isKeyGenerated = false;
        }
    }


    private boolean tryEncrypt() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);
            Cipher cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            // Try encrypting something, it will only work if the user authenticated within
            // the last AUTHENTICATION_DURATION_SECONDS seconds.
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            cipher.doFinal(SECRET_BYTE_ARRAY);

            // If the user has recently authenticated, you will reach here.
            //showAlreadyAuthenticated();
            authCallback.onAuthenticationSuccessful();
            return true;
        } catch (UserNotAuthenticatedException e) {
            // User is not authenticated, let's authenticate with device credentials.
            if (isKeyGenerated)
                showAuthenticationScreen();
            return false;
        } catch (BadPaddingException | IllegalBlockSizeException |
                KeyStoreException | CertificateException |
                UnrecoverableKeyException | IOException |
                NoSuchPaddingException |
                NoSuchAlgorithmException |
                InvalidKeyException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            return false;
        }//throw new RuntimeException(e);

    }

    private void showAuthenticationScreen() {
        // Create the Confirm Credentials screen.
        Intent intent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intent = mKeyguardManager.createConfirmDeviceCredentialIntent(title, description);
        }
        if (intent != null) {
            Activity activity = (Activity) context;
            activity.startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);

        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (isKeyGenerated) {
                isKeyGenerated = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!tryEncrypt()) {
                        authCallback.onAuthenticationFailed();
                    }
                } else {
                    authCallback.onAuthenticationFailed();
                }
            } else {
                authCallback.onAuthenticationFailed();
            }
        }
    }

    public static class AuthMangerBuilder {
        private String title;
        private String subtitle;
        private String description;
        private String negativeButtonText;
        private Boolean confirmationRequired;
        private Context context;

        public AuthMangerBuilder(Context context) {
            this.context = context;
        }

        public AuthMangerBuilder setTitle(String title) {
            this.title = title;
            return this;
        }

        public AuthMangerBuilder setSubtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public AuthMangerBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        public AuthMangerBuilder setNegativeButtonText(String negativeButtonText) {
            this.negativeButtonText = negativeButtonText;
            return this;
        }

        public AuthMangerBuilder setConfirmationRequired(Boolean confirmationRequired) {
            this.confirmationRequired = confirmationRequired;
            return this;
        }

        public AuthManager Builder() {
            return new AuthManager(this);
        }


    }

    public class BiometricAuthCallback extends BiometricPrompt.AuthenticationCallback {


        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
            //code 3 for authentication cancel by user
            //code 12 This device does not have a fingerprint sensor
            //code 13 cancel by user on nugat
            //code 11 No fingerprints enrolled
            //
            if (errorCode == 11 || errorCode == 12 || errorCode == 13 || errorCode == 10 || errorCode == 3) {

                //check user in M > < P and user allow confirmation required
                if (!AuthUtils.isPromptEnabled() && confirmationRequired) {
                    //check another authentication available or not, secret key generate for pin/password authentication
                    if (isKeyGenerated) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tryEncrypt();
                        }
                    } else {
                        authCallback.onAuthenticationError(errorCode, errString);
                    }
                } else {
                    authCallback.onAuthenticationError(errorCode, errString);
                }
            } else {
                authCallback.onAuthenticationError(errorCode, errString);
            }

        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            authCallback.onAuthenticationSuccessful();
        }

        @Override
        public void onAuthenticationFailed() {
            super.onAuthenticationFailed();
            authCallback.onAuthenticationFailed();
        }
    }
}
