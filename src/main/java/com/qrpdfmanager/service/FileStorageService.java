package com.qrpdfmanager.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.qrpdfmanager.config.FileStorageConfig;
import com.qrpdfmanager.exception.FileStorageException;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final Path tempStorageLocation;

    @Autowired
    public FileStorageService(FileStorageConfig fileStorageConfig) {
        this.fileStorageLocation = Paths.get(fileStorageConfig.getUploadDir())
                .toAbsolutePath().normalize();
        this.tempStorageLocation = Paths.get(fileStorageConfig.getTempDir())
                .toAbsolutePath().normalize();
                
        try {
            Files.createDirectories(this.fileStorageLocation);
            Files.createDirectories(this.tempStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directories where the uploaded files will be stored.", ex);
        }
    }

    /**
     * Stores a file in the temporary directory
     * 
     * @param file The file to store
     * @return The path to the stored file
     */
    public String storeFileTemporary(MultipartFile file) {
        // Normalize file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            // Check if the file's name contains invalid characters
            if (originalFileName.contains("..")) {
                throw new FileStorageException("Filename contains invalid path sequence " + originalFileName);
            }

            // Check if the file is a PDF
            if (!fileExtension.equalsIgnoreCase(".pdf")) {
                throw new FileStorageException("Only PDF files are supported");
            }

            // Copy file to the target location (Replacing existing file with the same name)
            Path targetLocation = this.tempStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    /**
     * Creates a directory for storing extracted pages
     * 
     * @param pageIndex The page index to use as directory name
     * @return The path to the created directory
     */
    public String createPageDirectory(int pageIndex) {
        String sessionId = UUID.randomUUID().toString();
        Path pageDir = this.fileStorageLocation.resolve("page_" + pageIndex);
        
        try {
            Files.createDirectories(pageDir);
            return pageDir.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not create directory for page " + pageIndex, ex);
        }
    }

    /**
     * Saves a page to the specified directory
     * 
     * @param pageBytes The page content as a byte array
     * @param directory The directory to save the page to
     * @param pageIndex The page index
     * @return The path to the saved page
     */
    public String savePage(byte[] pageBytes, String directory, int pageIndex) {
        try {
            String fileName = "page_" + pageIndex + ".pdf";
            Path filePath = Paths.get(directory).resolve(fileName);
            Files.write(filePath, pageBytes);
            return filePath.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not save page " + pageIndex, ex);
        }
    }

    /**
     * Gets the file extension from a filename
     * 
     * @param fileName The filename
     * @return The file extension including the dot
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
    
    /**
     * Creates a unique session directory for the current upload
     * 
     * @return The path to the created session directory
     */
    public String createSessionDirectory() {
        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = this.fileStorageLocation.resolve(sessionId);
        
        try {
            Files.createDirectories(sessionDir);
            return sessionDir.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not create session directory", ex);
        }
    }
    
    /**
     * Deletes a file
     * 
     * @param filePath The path to the file to delete
     */
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ex) {
            // Log but don't throw - this is cleanup
            System.err.println("Could not delete file " + filePath + ": " + ex.getMessage());
        }
    }
}
