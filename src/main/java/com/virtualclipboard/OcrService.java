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

    public String getSearchUrl(BufferedImage image, String engine) throws IOException {
        // First upload image to get direct URL
        String imageUrl = uploadImageToCatbox(image);

        // Construct search URL based on engine
        switch (engine) {
            case "Google":
                return getGoogleSearchUrl(imageUrl);
            case "Yandex":
                return getYandexSearchUrl(imageUrl);
            case "Bing":
                return getBingSearchUrl(imageUrl);
            default:
                return getGoogleSearchUrl(imageUrl);
        }
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
            return "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&sbisrc=UrlPaste&q=imgurl:" + encodedUrl;
        } catch (Exception e) {
            throw new IOException("Failed to encode URL for Bing", e);
        }
    }
}
