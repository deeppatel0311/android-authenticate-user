package com.biometricauth;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.biometricauthuser.AuthCallback;
import com.biometricauthuser.AuthManager;


public class MainActivity extends AppCompatActivity implements AuthCallback {

    AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = new AuthManager.AuthMangerBuilder(MainActivity.this)
                .setTitle(getResources().getString(R.string.auth_manager_title))
                .setSubtitle(getResources().getString(R.string.auth_manager_subtitle))
                .setDescription(getResources().getString(R.string.auth_manager_description))
                .setNegativeButtonText(getResources().getString(R.string.auth_manager_negative_text))
                .setConfirmationRequired(true)
                .Builder();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void BtnClick(View view) {
        authManager.authenticate(MainActivity.this);
    }

    @Override
    public void onSdkVersionNotSupported() {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.sdk_not_support),Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationPermissionNotGranted() {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.permission_not_granted),Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationInternalError(String error) {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.internal_error) + ": " + error,Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationFailed() {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.authentication_failed),Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationSuccessful() {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.authentication_success),Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationError(int errorCode, CharSequence errString) {
        Toast.makeText(MainActivity.this,getResources().getString(R.string.authentication_error) + ": " + errString,Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            authManager.onActivityResult(requestCode,resultCode,data);
        }
    }
}
