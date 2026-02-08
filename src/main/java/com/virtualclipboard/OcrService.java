package com.virtualclipboard;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import javax.imageio.ImageIO;

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

    public String getSearchUrl(BufferedImage image) throws IOException {
        String boundary = "---" + UUID.randomUUID().toString();
        String urlString = "https://www.google.com/searchbyimage/upload";
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        try (OutputStream output = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {

            // Image data
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"encoded_image\"; filename=\"image.png\"")
                    .append("\r\n");
            writer.append("Content-Type: image/png").append("\r\n");
            writer.append("\r\n").flush();

            ImageIO.write(image, "png", output);
            output.flush();
            writer.append("\r\n");

            // Source parameter
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"sbisrc\"").append("\r\n");
            writer.append("\r\n");
            writer.append("cr_1_0_0").append("\r\n");

            writer.append("--").append(boundary).append("--").append("\r\n").flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
            return connection.getHeaderField("Location");
        }

        throw new IOException("Failed to get search URL: " + responseCode);
    }
}
