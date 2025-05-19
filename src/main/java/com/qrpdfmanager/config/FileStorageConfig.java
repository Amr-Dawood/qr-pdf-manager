package com.qrpdfmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "file")
@Getter
@Setter
public class FileStorageConfig {
    private String uploadDir;
    private String tempDir;
    private int maxFileSize;
    private String allowedFileTypes;
}
