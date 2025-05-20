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
    

    public byte[] generateQrCodesForPdf(MultipartFile pdfFile) {
        try {
            String tempFilePath = fileStorageService.storeFileTemporary(pdfFile);
            byte[] pdfBytes = Files.readAllBytes(Paths.get(tempFilePath));
            
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pdfBytes)) {
                int numberOfPages = countPages(pdfBytes);
                
                byte[][] qrCodes = new byte[numberOfPages][];
                for (int i = 0; i < numberOfPages; i++) {
                    Map<String, Object> pageInfo = new HashMap<>();
                    pageInfo.put("pageIndex", i);
                    String pageInfoJson = objectMapper.writeValueAsString(pageInfo);
                    
                    qrCodes[i] = qrCodeUtil.generateQrCode(pageInfoJson);
                }
                
                byte[] modifiedPdf = pdfUtil.embedQrCodesIntoPdf(new ByteArrayInputStream(pdfBytes), qrCodes);
                
                fileStorageService.deleteFile(tempFilePath);
                
                return modifiedPdf;
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to process PDF file", e);
        }
    }
    

    public List<PageInfo> processPdfWithQrCodes(MultipartFile pdfFile) {
        try {
            String tempFilePath = fileStorageService.storeFileTemporary(pdfFile);
            byte[] pdfBytes = Files.readAllBytes(Paths.get(tempFilePath));
            
            List<BufferedImage> qrCodeImages = pdfUtil.extractQrCodesFromPdf(pdfBytes);
            List<PageInfo> pageInfoList = new ArrayList<>();
            
            String sessionDir = fileStorageService.createSessionDirectory();
            
            for (int i = 0; i < qrCodeImages.size(); i++) {
                try {
                    String qrCodeContent = qrCodeUtil.readQrCode(qrCodeImages.get(i));
                    
                    Map<String, Object> pageInfoMap = objectMapper.readValue(qrCodeContent, Map.class);
                    int pageIndex = (int) pageInfoMap.get("pageIndex");
                    
                    String pageDir = Paths.get(sessionDir, "page_" + pageIndex).toString();
                    Files.createDirectories(Paths.get(pageDir));
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    pdfUtil.extractPageFromPdf(pdfBytes, i, baos);
                    
                    String pagePath = fileStorageService.savePage(baos.toByteArray(), pageDir, pageIndex);
                    
                    pageInfoList.add(new PageInfo(pageIndex, pagePath));
                } catch (QrCodeException e) {
                    String pageDir = Paths.get(sessionDir, "page_unknown_" + i).toString();
                    Files.createDirectories(Paths.get(pageDir));
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    pdfUtil.extractPageFromPdf(pdfBytes, i, baos);
                    
                    String pagePath = fileStorageService.savePage(baos.toByteArray(), pageDir, i);
                    pageInfoList.add(new PageInfo(-1, pagePath)); // Use -1 to indicate unknown index
                }
            }
            
            fileStorageService.deleteFile(tempFilePath);
            
            return pageInfoList;
        } catch (IOException e) {
            throw new FileStorageException("Failed to process PDF file with QR codes", e);
        }
    }
    

    private int countPages(byte[] pdfBytes) {
        try {
            try (org.apache.pdfbox.pdmodel.PDDocument document =
                    org.apache.pdfbox.pdmodel.PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
                return document.getNumberOfPages();
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to count pages in PDF", e);
        }
    }
}
