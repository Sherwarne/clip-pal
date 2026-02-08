package com.virtualclipboard;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OcrService {
    private final Tesseract tesseract;
    private static final String TESSDATA_PATH = "tessdata";
    private static final String DATA_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata";

    public OcrService() {
        this.tesseract = new Tesseract();
        setupTessData();
    }

    private void setupTessData() {
        File dataDir = new File(TESSDATA_PATH);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File engData = new File(dataDir, "eng.traineddata");
        if (!engData.exists()) {
            System.out.println("Downloading Tesseract language data...");
            downloadData(engData);
        }

        tesseract.setDatapath(dataDir.getAbsolutePath());
        tesseract.setLanguage("eng");
    }

    private void downloadData(File target) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(DATA_URL).openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            System.out.println("Download complete.");
        } catch (IOException e) {
            System.err.println("Failed to download Tesseract data: " + e.getMessage());
        }
    }

    public String extractText(BufferedImage image) throws TesseractException {
        return tesseract.doOCR(image);
    }
}
