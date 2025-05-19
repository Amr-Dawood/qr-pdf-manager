package com.qrpdfmanager.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import com.qrpdfmanager.exception.FileStorageException;

import javax.imageio.ImageIO;

@Component
public class PdfUtil {

    /**
     * Embeds QR codes into each page of a PDF document
     * 
     * @param pdfInputStream The input PDF document stream
     * @param qrCodes Array of QR code images as byte arrays, one for each page
     * @return The modified PDF document as a byte array
     */
    public byte[] embedQrCodesIntoPdf(InputStream pdfInputStream, byte[][] qrCodes) {
        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            int numberOfPages = document.getNumberOfPages();
            
            if (qrCodes.length != numberOfPages) {
                throw new FileStorageException("Number of QR codes does not match number of pages");
            }
            
            for (int i = 0; i < numberOfPages; i++) {
                PDPage page = document.getPage(i);
                PDRectangle pageSize = page.getMediaBox();
                
                // Convert QR code bytes to BufferedImage for lossless embedding
                BufferedImage qrCodeBufferedImage = ImageIO.read(new ByteArrayInputStream(qrCodes[i]));
                
                // Create image from QR code using LosslessFactory to preserve quality
                PDImageXObject qrCodeImage = LosslessFactory.createFromImage(
                    document, 
                    qrCodeBufferedImage
                );
                
                // Calculate position (bottom right corner with increased margin)
                // Increased QR code size from 100 to 200 for better readability
                float qrCodeWidth = 200;
                float qrCodeHeight = 200;
                
                // PDF coordinates start from bottom-left corner
                // Position QR code in bottom right with 40 point margin
                float xPosition = pageSize.getWidth() - qrCodeWidth - 40;
                float yPosition = 40; // 40 points from bottom
                
                // Add QR code to the page
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, 
                        page, 
                        AppendMode.APPEND, 
                        true, 
                        true)) {
                    contentStream.drawImage(
                        qrCodeImage, 
                        xPosition, 
                        yPosition, 
                        qrCodeWidth, 
                        qrCodeHeight
                    );
                }
            }
            
            // Save the modified document with high quality settings
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new FileStorageException("Failed to process PDF document", e);
        }
    }
    
    /**
     * Extracts QR codes from each page of a PDF document
     * 
     * @param pdfBytes The PDF document as a byte array
     * @return List of BufferedImage objects, one for each QR code found
     */
    public List<BufferedImage> extractQrCodesFromPdf(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int numberOfPages = document.getNumberOfPages();
            List<BufferedImage> qrCodeImages = new ArrayList<>();
            
            for (int i = 0; i < numberOfPages; i++) {
                // Render the page at a higher DPI for better QR code recognition
                // Increased from 300 to 600 DPI for more reliable QR code extraction
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 600);
                
                // Extract the bottom right corner where QR code is expected
                int width = pageImage.getWidth();
                int height = pageImage.getHeight();
                
                // Calculate QR code position in rendered image
                // Match the embedding coordinates (bottom right with 40px margin)
                int qrSize = 200 * 600 / 72; // Convert from points to pixels at 600 DPI
                int margin = 40 * 600 / 72;  // Convert margin from points to pixels
                
                // PDF coordinates start from bottom-left, but image is top-left
                int x = width - qrSize - margin;
                int y = height - qrSize - margin;
                
                // Ensure coordinates are within image bounds
                x = Math.max(0, x);
                y = Math.max(0, y);
                qrSize = Math.min(qrSize, Math.min(width - x, height - y));
                
                // Extract the region containing the QR code
                BufferedImage qrCodeImage = pageImage.getSubimage(x, y, qrSize, qrSize);
                qrCodeImages.add(qrCodeImage);
            }
            
            return qrCodeImages;
        } catch (IOException e) {
            throw new FileStorageException("Failed to extract QR codes from PDF", e);
        }
    }
    
    /**
     * Extracts a single page from a PDF document
     * 
     * @param pdfBytes The PDF document as a byte array
     * @param pageIndex The zero-based index of the page to extract
     * @param outputStream The output stream to write the extracted page to
     */
    public void extractPageFromPdf(byte[] pdfBytes, int pageIndex, OutputStream outputStream) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int numberOfPages = document.getNumberOfPages();
            
            if (pageIndex < 0 || pageIndex >= numberOfPages) {
                throw new FileStorageException("Invalid page index: " + pageIndex);
            }
            
            PDDocument singlePageDoc = new PDDocument();
            singlePageDoc.addPage(document.getPage(pageIndex));
            singlePageDoc.save(outputStream);
            singlePageDoc.close();
        } catch (IOException e) {
            throw new FileStorageException("Failed to extract page from PDF", e);
        }
    }
}
