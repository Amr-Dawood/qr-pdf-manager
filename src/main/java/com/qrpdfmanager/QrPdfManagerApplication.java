package com.qrpdfmanager;

import com.qrpdfmanager.config.FileStorageConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableConfigurationProperties({
        FileStorageConfig.class
})

public class QrPdfManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrPdfManagerApplication.class, args);
    }

}
