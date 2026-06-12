package com.clinic.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TOTPService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    @Value("${otp.app-name:PhongKhamDemo}")
    private String appName;

    public String generateSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    public boolean verifyOTP(String secretKey, int otpCode) {
        return gAuth.authorize(secretKey, otpCode);
    }

    public String getQRCodeUrl(String username, String secretKey) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
            appName, username, new GoogleAuthenticatorKey.Builder(secretKey).build());
    }
}
