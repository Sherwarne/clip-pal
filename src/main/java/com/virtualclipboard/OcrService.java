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
import java.util.function.BiConsumer;
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
        try {
            URL url = new URL(DATA_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(60000); // 60s for download

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(target)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
                System.out.println("Download complete.");
            }
        } catch (IOException e) {
            System.err.println("Failed to download Tesseract data: " + e.getMessage());
        }
    }

    public String extractText(BufferedImage image, BiConsumer<Integer, String> progressListener) throws TesseractException {
        if (progressListener != null) progressListener.accept(20, "Preparing image for OCR...");
        String result = tesseract.doOCR(image);
        if (progressListener != null) progressListener.accept(100, "Extraction complete");
        return result;
    }

    public String getSearchUrl(BufferedImage image, String engine, BiConsumer<Integer, String> progressListener) throws IOException {
        // Try multiple upload services for robustness
        String imageUrl = null;
        Exception lastException = null;

        // Try Catbox first
        try {
            if (progressListener != null) progressListener.accept(10, "Uploading to primary service (Catbox)...");
            imageUrl = uploadImageToCatbox(image);
        } catch (Exception e) {
            System.err.println("Catbox upload failed, trying fallback: " + e.getMessage());
            lastException = e;
        }

        // Try Litterbox fallback
        if (imageUrl == null) {
            try {
                if (progressListener != null) progressListener.accept(40, "Primary service slow, trying fallback (Litterbox)...");
                imageUrl = uploadImageToLitterbox(image);
            } catch (Exception e) {
                System.err.println("Litterbox upload failed: " + e.getMessage());
                lastException = e;
            }
        }

        if (imageUrl == null) {
            throw new IOException("Failed to upload image to any service. Last error: " + 
                (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
        }

        if (progressListener != null) progressListener.accept(90, "Finalizing search parameters...");

        // Construct search URL based on engine
        String resultUrl;
        switch (engine) {
            case "Google":
                resultUrl = getGoogleSearchUrl(imageUrl);
                break;
            case "Yandex":
                resultUrl = getYandexSearchUrl(imageUrl);
                break;
            case "Bing":
                resultUrl = getBingSearchUrl(imageUrl);
                break;
            default:
                resultUrl = getGoogleSearchUrl(imageUrl);
        }

        if (progressListener != null) progressListener.accept(100, "Search ready");
        return resultUrl;
    }

    private String uploadImageToLitterbox(BufferedImage image) throws IOException {
        String boundary = "---" + UUID.randomUUID().toString();
        URL url = new URL("https://litterbox.catbox.moe/resources/internals/api.php");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream()) {

            ImageIO.write(image, "png", imageBytes);
            byte[] imageData = imageBytes.toByteArray();

            // reqtype
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n");
            output.writeBytes("fileupload\r\n");

            // time (1 hour for temporary storage)
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"time\"\r\n\r\n");
            output.writeBytes("1h\r\n");

            // fileToUpload
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"image.png\"\r\n");
            output.writeBytes("Content-Type: image/png\r\n\r\n");
            output.write(imageData);
            output.writeBytes("\r\n");

            output.writeBytes("--" + boundary + "--\r\n");
            output.flush();
        }

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String res = reader.readLine();
                if (res != null && res.startsWith("http")) return res.trim();
            }
        }
        throw new IOException("Litterbox upload failed: " + connection.getResponseCode());
    }

    private String getGoogleSearchUrl(String imageUrl) throws IOException {
        try {
            String encodedUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8");
            return "https://lens.google.com/uploadbyurl?url=" + encodedUrl;
        } catch (Exception e) {
            throw new IOException("Failed to encode URL for Google", e);
        }
    }

    private String uploadImageToCatbox(BufferedImage image) throws IOException {
        String boundary = "---" + UUID.randomUUID().toString();
        URL url = new URL("https://catbox.moe/user/api.php");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // Add timeouts to prevent hanging
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(30000);    // 30 seconds
        
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream()) {

            // Convert image to PNG
            ImageIO.write(image, "png", imageBytes);
            byte[] imageData = imageBytes.toByteArray();

            // reqtype parameter
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n");
            output.writeBytes("\r\n");
            output.writeBytes("fileupload\r\n");

            // fileToUpload parameter
            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"image.png\"\r\n");
            output.writeBytes("Content-Type: image/png\r\n");
            output.writeBytes("\r\n");
            output.write(imageData);
            output.writeBytes("\r\n");

            // End boundary
            output.writeBytes("--" + boundary + "--\r\n");
            output.flush();
        }

        // Read response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String uploadedUrl = reader.readLine();
                if (uploadedUrl != null && !uploadedUrl.isEmpty()) {
                    return uploadedUrl.trim();
                }
            }
        }

        throw new IOException("Failed to upload image to Catbox: " + responseCode);
    }

    private String getYandexSearchUrl(String imageUrl) throws IOException {
        try {
            String encodedUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8");
            return "https://yandex.com/images/search?rpt=imageview&url=" + encodedUrl;
        } catch (Exception e) {
            throw new IOException("Failed to encode URL for Yandex", e);
        }
    }

    private String getBingSearchUrl(String imageUrl) throws IOException {
        try {
            String encodedUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8");
            // Enhanced Bing URL with more robust parameters to trigger full visual search results
            return "https://www.bing.com/images/search?view=detailv2&iss=sbi&imgurl=" + encodedUrl + "&selectedindex=0&id=" + encodedUrl;
        } catch (Exception e) {
            throw new IOException("Failed to encode URL for Bing", e);
        }
    }
}
