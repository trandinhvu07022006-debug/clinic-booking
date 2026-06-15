package com.clinic.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * TOTPService — Xác thực 2 yếu tố (2FA) cho tài khoản BÁC SĨ (RFC 6238).
 *
 * Đây là minh họa thực tế cho Chương 3 (TOTP) và mô hình Risk-based Authentication:
 *   - Bệnh nhân (rủi ro thấp)  -> OTP đơn qua email/console (AppointmentOtpService)
 *   - Bác sĩ   (rủi ro cao)    -> 2FA bằng TOTP (mã từ Google/Microsoft Authenticator)
 *
 * Cấu hình tường minh các tham số đã trình bày ở lý thuyết:
 *   - Time-step  = 30 giây            (mục 3.2.2)
 *   - WindowSize = 3                  (Tolerance Window: chấp nhận ±1 chu kỳ,
 *                                      bù trừ lệch đồng hồ/độ trễ mạng — mục 3.2.2)
 */
@Service
public class TOTPService {

    private final GoogleAuthenticator gAuth;

    @Value("${otp.app-name:PhongKhamDemo}")
    private String appName;

    public TOTPService() {
        GoogleAuthenticatorConfig config =
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30)) // chu kỳ 30s
                .setWindowSize(3)                                       // ±1 chu kỳ
                .setCodeDigits(6)                                       // mã 6 số
                .build();
        this.gAuth = new GoogleAuthenticator(config);
    }

    /** Sinh Secret Key (Base32) khi tạo tài khoản bác sĩ. */
    public String generateSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    /** Xác thực mã TOTP 6 số bác sĩ nhập từ app Authenticator. */
    public boolean verifyOTP(String secretKey, int otpCode) {
        return gAuth.authorize(secretKey, otpCode);
    }

    /**
     * Tạo URL otpauth:// để render QR Code (Provisioning — mục 2.3.1).
     * Bác sĩ quét QR này một lần bằng Google Authenticator để nạp Secret Key.
     */
    public String getQRCodeUrl(String username, String secretKey) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
            appName, username,
            new GoogleAuthenticatorKey.Builder(secretKey).build());
    }
}
