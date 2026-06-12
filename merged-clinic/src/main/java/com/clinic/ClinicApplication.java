package com.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClinicApplication {

    public static void main(String[] args) {
        // BẮT BUỘC THÊM DÒNG NÀY ĐỂ SỬA LỖI TIMEOUT TRÊN RENDER
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        SpringApplication.run(ClinicApplication.class, args);
    }
}