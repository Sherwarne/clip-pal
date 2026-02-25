package com.virtualclipboard;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

public class OllamaService {
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private final ConfigManager configManager;
    private final HttpClient client;
    private final ExecutorService executor;

    public OllamaService(ConfigManager configManager) {
        this.configManager = configManager;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = Executors.newFixedThreadPool(2); // Limit concurrent requests
    }

    public CompletableFuture<String> generateCaption(String text) {
        if (!configManager.isAiCaptionEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return checkConnection().thenCompose(isRunning -> {
            if (!isRunning) {
                return CompletableFuture.completedFuture(null);
            }
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Truncate input to avoid excessive context
                    String safeText = text.length() > 2000 ? text.substring(0, 2000) : text;
                    
                    // Simple JSON escaping
                    String escapedText = safeText.replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", " ")
                                                .replace("\r", "");

                    String configuredModel = configManager.getOllamaModel();
                    
                    // Check if model exists, if not find best match
                    String model = resolveModel(configuredModel);
                    if (model == null) {
                        System.err.println("OllamaService: No valid model found. Configured: " + configuredModel);
                        return null;
                    }

                    System.out.println("OllamaService: Generating caption using model '" + model + "' for text (" + safeText.length() + " chars): " + 
                        (safeText.length() > 50 ? safeText.substring(0, 50) + "..." : safeText));
                    
                    String jsonBody = String.format(
                        "{" +
                        "\"model\": \"%s\"," +
                        "\"prompt\": \"Generate a very short, concise caption (max 10-15 words) for the following text. Do not use quotes. Text: %s\"," +
                        "\"stream\": false" +
                        "}", model, escapedText);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(OLLAMA_API_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .timeout(Duration.ofSeconds(30))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String result = extractResponse(response.body());
                        System.out.println("OllamaService: Generated caption: " + result);
                        return result;
                    } else {
                        System.err.println("OllamaService: API Error: " + response.statusCode());
                        return null;
                    }
                } catch (Exception e) {
                    // e.printStackTrace(); // Suppress generic connection errors to avoid console spam if Ollama isn't running
                    return null;
                }
            }, executor);
        });
    }

    public CompletableFuture<String> generateImageCaption(BufferedImage image) {
        if (!configManager.isAiCaptionEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return checkConnection().thenCompose(isRunning -> {
            if (!isRunning) {
                return CompletableFuture.completedFuture(null);
            }
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Convert image to base64 - use smaller size for speed
                    String base64Image = imageToBase64(image, 512); // Reduced to 512px for speed
                    if (base64Image == null) {
                        System.err.println("OllamaService: Failed to encode image to base64");
                        return null;
                    }

                    String configuredModel = configManager.getOllamaModel();
                    
                    // For image captioning, we need a vision-capable model
                    // Try to find a vision model, fallback to configured model
                    String model = resolveVisionModel(configuredModel);
                    if (model == null) {
                        System.err.println("OllamaService: No vision model found. Configured: " + configuredModel);
                        return null;
                    }

                    System.out.println("OllamaService: Generating image caption using model '" + model + "'");
                    
                    // JSON body with image data for vision models - shorter prompt for speed
                    String jsonBody = String.format(
                        "{\n" +
                        "  \"model\": \"%s\",\n" +
                        "  \"prompt\": \"Briefly describe this image in 5-10 words.\",\n" +
                        "  \"images\": [\"%s\"],\n" +
                        "  \"stream\": false,\n" +
                        "  \"options\": {\"num_ctx\": 2048, \"temperature\": 0.5}\n" +
                        "}", model, base64Image);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(OLLAMA_API_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .timeout(Duration.ofSeconds(45)) // Reduced timeout for faster response
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String result = extractResponse(response.body());
                        System.out.println("OllamaService: Generated image caption: " + result);
                        return result;
                    } else {
                        System.err.println("OllamaService: Image API Error: " + response.statusCode() + " - " + response.body());
                        return null;
                    }
                } catch (Exception e) {
                    System.err.println("OllamaService: Exception generating image caption: " + e.getMessage());
                    return null;
                }
            }, executor);
        });
    }

    private String imageToBase64(BufferedImage image, int maxSize) {
        try {
            // Resize image to reduce size for faster processing
            BufferedImage resized = resizeImage(image, maxSize);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private BufferedImage resizeImage(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        if (width <= maxSize && height <= maxSize) {
            return original;
        }
        
        double scale = Math.min((double) maxSize / width, (double) maxSize / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return resized;
    }

    private String resolveVisionModel(String preferred) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                List<String> models = new ArrayList<>();
                // Extract model names
                int index = 0;
                while ((index = json.indexOf("\"name\":\"", index)) != -1) {
                    index += 8; 
                    int end = json.indexOf("\"", index);
                    if (end != -1) {
                        String name = json.substring(index, end);
                        models.add(name);
                        index = end;
                    }
                }
                
                if (models.isEmpty()) {
                    System.err.println("OllamaService: No models found in Ollama");
                    return null;
                }

                // Known vision models in Ollama
                String[] visionModelKeywords = {"llava", "llama3.2-vision", "qwen2.5-vl", "qwen2-vl", "phi4-mini", "vision", "visual", "moondream"};
                
                // 1. Check exact match with preferred if it's a vision model
                if (models.contains(preferred)) {
                    String lowerPreferred = preferred.toLowerCase();
                    boolean isVision = false;
                    for (String keyword : visionModelKeywords) {
                        if (lowerPreferred.contains(keyword.toLowerCase())) {
                            isVision = true;
                            break;
                        }
                    }
                    if (isVision) return preferred;
                }
                
                // 2. Check for vision-capable models
                for (String m : models) {
                    String lowerM = m.toLowerCase();
                    for (String keyword : visionModelKeywords) {
                        if (lowerM.contains(keyword.toLowerCase())) {
                            System.out.println("OllamaService: Selected vision model: " + m);
                            return m;
                        }
                    }
                }
                
                // 3. No vision model found - return null to indicate failure
                System.err.println("OllamaService: No vision models found. Available: " + models);
                System.err.println("OllamaService: Please install a vision model like 'llava' or 'llama3.2-vision'");
                return null;
            }
        } catch (Exception e) {
            System.err.println("OllamaService: Error checking for vision models: " + e.getMessage());
        }
        return null;
    }

    private String resolveModel(String preferred) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                List<String> models = new ArrayList<>();
                // Extract model names
                int index = 0;
                while ((index = json.indexOf("\"name\":\"", index)) != -1) {
                    index += 8; 
                    int end = json.indexOf("\"", index);
                    if (end != -1) {
                        String name = json.substring(index, end);
                        models.add(name);
                        index = end;
                    }
                }
                
                if (models.isEmpty()) return preferred; // Optimistic fallback

                // 1. Check exact match
                if (models.contains(preferred)) return preferred;
                
                // 2. Check for tag match (e.g. "llama3" matches "llama3:latest" or "llama3.1:8b")
                for (String m : models) {
                    if (m.startsWith(preferred) || m.contains(preferred)) return m;
                }
                
                // 3. Fallback to any non-embedding model if preferred is not found
                for (String m : models) {
                    if (!m.contains("embed") && !m.contains("bert")) return m;
                }
                
                // 4. Return first model as last resort
                return models.get(0);
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return preferred; // Default to configured if check fails
    }

    public CompletableFuture<List<String>> getAvailableModels() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> models = new ArrayList<>();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:11434/api/tags"))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String json = response.body();
                    int index = 0;
                    while ((index = json.indexOf("\"name\":\"", index)) != -1) {
                        index += 8; 
                        int end = json.indexOf("\"", index);
                        if (end != -1) {
                            String name = json.substring(index, end);
                            models.add(name);
                            index = end;
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            return models;
        }, executor);
    }

    public CompletableFuture<Boolean> checkConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:11434/api/tags")) // Use tags endpoint to check connection
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }, executor);
    }

    private String extractResponse(String json) {
        // Simple manual parsing for "response": "..."
        try {
            String key = "\"response\":\"";
            int start = json.indexOf(key);
            if (start == -1) {
                // Try with spaces
                key = "\"response\": \"";
                start = json.indexOf(key);
            }
            
            if (start != -1) {
                start += key.length();
                int end = start;
                boolean escaped = false;
                // Find closing quote, handling escaped quotes
                while (end < json.length()) {
                    char c = json.charAt(end);
                    if (c == '\\') {
                        escaped = !escaped;
                    } else if (c == '"' && !escaped) {
                        break;
                    } else {
                        escaped = false;
                    }
                    end++;
                }
                
                if (end < json.length()) {
                    String content = json.substring(start, end);
                    // Unescape
                    return content.replace("\\\"", "\"")
                                  .replace("\\n", "\n")
                                  .replace("\\\\", "\\")
                                  .trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
