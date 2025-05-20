package com.qrpdfmanager.controller;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.qrpdfmanager.model.ApiResponse;
import com.qrpdfmanager.model.PageInfo;
import com.qrpdfmanager.service.QrCodeService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    @Autowired
    private QrCodeService qrCodeService;

    @PostMapping("/download")
    public ResponseEntity<?> downloadPdfWithQrCodes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Please upload a PDF file"));
        }
        
        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only PDF files are supported"));
        }
        
        try {
            byte[] pdfWithQrCodes = qrCodeService.generateQrCodesForPdf(file);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "document_with_qrcodes.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(new ByteArrayInputStream(pdfWithQrCodes)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process PDF: " + e.getMessage()));
        }
    }
    

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdfWithQrCodes(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Please upload a PDF file"));
        }
        
        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only PDF files are supported"));
        }
        
        try {
            List<PageInfo> pageInfoList = qrCodeService.processPdfWithQrCodes(file);
            
            return ResponseEntity.ok()
                    .body(ApiResponse.success("PDF processed successfully", pageInfoList));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process PDF: " + e.getMessage()));
        }
    }
}
