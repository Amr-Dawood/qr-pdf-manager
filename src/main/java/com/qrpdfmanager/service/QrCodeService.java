package com.qrpdfmanager.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpdfmanager.exception.FileStorageException;
import com.qrpdfmanager.exception.QrCodeException;
import com.qrpdfmanager.model.PageInfo;
import com.qrpdfmanager.util.PdfUtil;
import com.qrpdfmanager.util.QrCodeUtil;

@Service
public class QrCodeService {

    @Autowired
    private QrCodeUtil qrCodeUtil;
    
    @Autowired
    private PdfUtil pdfUtil;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Generates QR codes for each page of a PDF document
     * 
     * @param pdfFile The PDF file
     * @return The modified PDF with QR codes embedded
     */
    public byte[] generateQrCodesForPdf(MultipartFile pdfFile) {
        try {
            // Store the file temporarily
            String tempFilePath = fileStorageService.storeFileTemporary(pdfFile);
            byte[] pdfBytes = Files.readAllBytes(Paths.get(tempFilePath));
            
            // Load the PDF document
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes)) {
                // Count the number of pages
                int numberOfPages = countPages(pdfBytes);
                
                // Generate QR codes for each page
                byte[][] qrCodes = new byte[numberOfPages][];
                for (int i = 0; i < numberOfPages; i++) {
                    // Create page info JSON
                    Map<String, Object> pageInfo = new HashMap<>();
                    pageInfo.put("pageIndex", i);
                    String pageInfoJson = objectMapper.writeValueAsString(pageInfo);
                    
                    // Generate QR code
                    qrCodes[i] = qrCodeUtil.generateQrCode(pageInfoJson);
                }
                
                // Embed QR codes into the PDF
                byte[] modifiedPdf = pdfUtil.embedQrCodesIntoPdf(new ByteArrayInputStream(pdfBytes), qrCodes);
                
                // Clean up temporary file
                fileStorageService.deleteFile(tempFilePath);
                
                return modifiedPdf;
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to process PDF file", e);
        }
    }
    
    /**
     * Processes a PDF document with QR codes and extracts pages into separate directories
     * 
     * @param pdfFile The PDF file with QR codes
     * @return List of page information objects
     */
    public List<PageInfo> processPdfWithQrCodes(MultipartFile pdfFile) {
        try {
            // Store the file temporarily
            String tempFilePath = fileStorageService.storeFileTemporary(pdfFile);
            byte[] pdfBytes = Files.readAllBytes(Paths.get(tempFilePath));
            
            // Extract QR codes from the PDF
            List<BufferedImage> qrCodeImages = pdfUtil.extractQrCodesFromPdf(pdfBytes);
            List<PageInfo> pageInfoList = new ArrayList<>();
            
            // Create a session directory for this upload
            String sessionDir = fileStorageService.createSessionDirectory();
            
            // Process each QR code
            for (int i = 0; i < qrCodeImages.size(); i++) {
                try {
                    // Read the QR code content
                    String qrCodeContent = qrCodeUtil.readQrCode(qrCodeImages.get(i));
                    
                    // Parse the page info from the QR code
                    Map<String, Object> pageInfoMap = objectMapper.readValue(qrCodeContent, Map.class);
                    int pageIndex = (int) pageInfoMap.get("pageIndex");
                    
                    // Create directory for this page index
                    String pageDir = Paths.get(sessionDir, "page_" + pageIndex).toString();
                    Files.createDirectories(Paths.get(pageDir));
                    
                    // Extract the page from the PDF
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    pdfUtil.extractPageFromPdf(pdfBytes, i, baos);
                    
                    // Save the page to the appropriate directory
                    String pagePath = fileStorageService.savePage(baos.toByteArray(), pageDir, pageIndex);
                    
                    // Add page info to the list
                    pageInfoList.add(new PageInfo(pageIndex, pagePath));
                } catch (QrCodeException e) {
                    // If QR code can't be read, use the current index as fallback
                    String pageDir = Paths.get(sessionDir, "page_unknown_" + i).toString();
                    Files.createDirectories(Paths.get(pageDir));
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    pdfUtil.extractPageFromPdf(pdfBytes, i, baos);
                    
                    String pagePath = fileStorageService.savePage(baos.toByteArray(), pageDir, i);
                    pageInfoList.add(new PageInfo(-1, pagePath)); // Use -1 to indicate unknown index
                }
            }
            
            // Clean up temporary file
            fileStorageService.deleteFile(tempFilePath);
            
            return pageInfoList;
        } catch (IOException e) {
            throw new FileStorageException("Failed to process PDF file with QR codes", e);
        }
    }
    
    /**
     * Counts the number of pages in a PDF document
     * 
     * @param pdfBytes The PDF document as a byte array
     * @return The number of pages
     */
    private int countPages(byte[] pdfBytes) {
        try {
            // Use PDFBox to count pages
            try (org.apache.pdfbox.pdmodel.PDDocument document = 
                    org.apache.pdfbox.pdmodel.PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
                return document.getNumberOfPages();
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to count pages in PDF", e);
        }
    }
}
