package com.qrpdfmanager.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrpdfmanager.exception.QrCodeException;

@Component
public class QrCodeUtil {

    // Increased QR code size from 100 to 200 for better readability
    private static final int QR_CODE_SIZE = 200;
    // Added white border size (in pixels)
    private static final int WHITE_BORDER = 20;

    public byte[] generateQrCode(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            // Using highest error correction level for better readability
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            // Increased margin from 1 to 4 (standard quiet zone)
            hints.put(EncodeHintType.MARGIN, 4);
            
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                content, 
                BarcodeFormat.QR_CODE, 
                QR_CODE_SIZE, 
                QR_CODE_SIZE, 
                hints
            );
            
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            
            // Add additional white border around the QR code
            BufferedImage borderedImage = addWhiteBorder(qrImage, WHITE_BORDER);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(borderedImage, "PNG", baos);
            
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new QrCodeException("Failed to generate QR code", e);
        }
    }
    
    private BufferedImage addWhiteBorder(BufferedImage original, int borderSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        // Create a new image with border
        BufferedImage bordered = new BufferedImage(
                width + 2 * borderSize, 
                height + 2 * borderSize,
                BufferedImage.TYPE_INT_RGB);
        
        // Fill the entire image with white
        Graphics2D g2d = bordered.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, bordered.getWidth(), bordered.getHeight());
        
        // Draw the original QR code in the center
        g2d.drawImage(original, borderSize, borderSize, null);
        g2d.dispose();
        
        return bordered;
    }
    
    public String readQrCode(BufferedImage image) {
        // Create a list to store all exceptions for better error reporting
        List<Exception> exceptions = new ArrayList<>();
        
        // Try with original image first
        try {
            String result = decodeQRCode(image);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            exceptions.add(e);
        }
        
        // Try with preprocessed images
        try {
            // Try with increased contrast
            BufferedImage highContrastImage = enhanceContrast(image);
            String result = decodeQRCode(highContrastImage);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            exceptions.add(e);
        }
        
        try {
            // Try with grayscale
            BufferedImage grayscaleImage = convertToGrayscale(image);
            String result = decodeQRCode(grayscaleImage);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            exceptions.add(e);
        }
        
        try {
            // Try with inverted colors
            BufferedImage invertedImage = invertColors(image);
            String result = decodeQRCode(invertedImage);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            exceptions.add(e);
        }
        
        // Try with different scales
        for (double scale : new double[] {1.5, 2.0, 0.75, 0.5}) {
            try {
                BufferedImage scaledImage = scaleImage(image, scale);
                String result = decodeQRCode(scaledImage);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        
        // If all attempts failed, throw an exception with details
        StringBuilder errorMessage = new StringBuilder("Failed to read QR code after multiple attempts. ");
        if (!exceptions.isEmpty()) {
            errorMessage.append("Last error: ").append(exceptions.get(exceptions.size() - 1).getMessage());
        }
        
        throw new QrCodeException(errorMessage.toString());
    }
    
    private String decodeQRCode(BufferedImage image) {
        // Try with HybridBinarizer first (better for high contrast images)
        try {
            return decodeWithBinarizer(image, new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        } catch (Exception e) {
            // Ignore and try next method
        }
        
        // Try with GlobalHistogramBinarizer (better for low contrast images)
        try {
            return decodeWithGlobalHistogramBinarizer(image, new GlobalHistogramBinarizer(new BufferedImageLuminanceSource(image)));
        } catch (Exception e) {
            // Ignore and try next method
        }
        
        // Try with rotated images
        for (int angle : new int[] {90, 180, 270}) {
            try {
                BufferedImage rotatedImage = rotateImage(image, angle);
                
                // Try both binarizers with rotated image
                try {
                    return decodeWithBinarizer(rotatedImage, 
                            new HybridBinarizer(new BufferedImageLuminanceSource(rotatedImage)));
                } catch (Exception e) {
                    // Ignore and try next method
                }
                
                try {
                    return decodeWithGlobalHistogramBinarizer(rotatedImage, 
                            new GlobalHistogramBinarizer(new BufferedImageLuminanceSource(rotatedImage)));
                } catch (Exception e) {
                    // Ignore and try next method
                }
                
            } catch (Exception e) {
                // Ignore rotation errors
            }
        }
        
        return null; // All attempts failed
    }
    

    private String decodeWithBinarizer(BufferedImage image, HybridBinarizer binarizer) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        
        BinaryBitmap bitmap = new BinaryBitmap(binarizer);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
    
    private String decodeWithGlobalHistogramBinarizer(BufferedImage image, GlobalHistogramBinarizer binarizer) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        
        BinaryBitmap bitmap = new BinaryBitmap(binarizer);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
    
    private BufferedImage enhanceContrast(BufferedImage original) {
        BufferedImage enhanced = new BufferedImage(
                original.getWidth(), 
                original.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = enhanced.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        
        // Apply contrast enhancement
        for (int x = 0; x < enhanced.getWidth(); x++) {
            for (int y = 0; y < enhanced.getHeight(); y++) {
                Color color = new Color(enhanced.getRGB(x, y));
                int red = color.getRed();
                int green = color.getGreen();
                int blue = color.getBlue();
                
                // Simple contrast enhancement
                red = Math.min(255, Math.max(0, (red - 128) * 2 + 128));
                green = Math.min(255, Math.max(0, (green - 128) * 2 + 128));
                blue = Math.min(255, Math.max(0, (blue - 128) * 2 + 128));
                
                enhanced.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        
        return enhanced;
    }
    
    private BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage grayscale = new BufferedImage(
                original.getWidth(), 
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        
        Graphics2D g2d = grayscale.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        
        return grayscale;
    }
    
    private BufferedImage invertColors(BufferedImage original) {
        BufferedImage inverted = new BufferedImage(
                original.getWidth(), 
                original.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        
        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                Color color = new Color(original.getRGB(x, y));
                int red = 255 - color.getRed();
                int green = 255 - color.getGreen();
                int blue = 255 - color.getBlue();
                
                inverted.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        
        return inverted;
    }
    
    private BufferedImage scaleImage(BufferedImage original, double scale) {
        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);
        
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return scaled;
    }
    
    private BufferedImage rotateImage(BufferedImage original, int degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        
        int newWidth = (int) Math.floor(original.getWidth() * cos + original.getHeight() * sin);
        int newHeight = (int) Math.floor(original.getHeight() * cos + original.getWidth() * sin);
        
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = rotated.createGraphics();
        
        g2d.translate((newWidth - original.getWidth()) / 2, (newHeight - original.getHeight()) / 2);
        g2d.rotate(radians, original.getWidth() / 2, original.getHeight() / 2);
        g2d.drawRenderedImage(original, null);
        g2d.dispose();
        
        return rotated;
    }
}
