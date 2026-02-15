package com.virtualclipboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Objects;

public class ClipboardItem implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Type {
        TEXT, IMAGE, URL, SVG, CODE
    }

    private final Type type;
    private final String text;
    private final String urlDomain;
    private final String urlProtocol;
    private final String codeLanguage;
    private transient BufferedImage image;
    private final LocalDateTime timestamp;
    private final long sizeInBytes;
    private final int width;
    private final int height;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (type == Type.IMAGE && image != null) {
            ImageIO.write(image, "png", out);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (type == Type.IMAGE) {
            image = ImageIO.read(in);
        }
    }

    public ClipboardItem(String text) {
        String processedText = text;
        if (text != null && text.contains("[InternetShortcut]") && text.contains("URL=")) {
            processedText = extractUrlFromShortcut(text);
        }

        this.text = processedText;
        this.image = null;
        this.timestamp = LocalDateTime.now();
        this.sizeInBytes = processedText.getBytes().length;
        this.width = -1;
        this.height = -1;

        String detectedLang = CodeDetector.detectLanguage(processedText);
        if (isSVG(processedText)) {
            this.type = Type.SVG;
            this.urlDomain = null;
            this.urlProtocol = null;
            this.codeLanguage = null;
        } else if (isURL(processedText)) {
            this.type = Type.URL;
            this.codeLanguage = null;
            String domain = "N/A";
            String protocol = "N/A";
            try {
                String spec = processedText.startsWith("http") ? processedText : "http://" + processedText;
                URL url = new URI(spec).toURL();
                domain = url.getHost();
                protocol = url.getProtocol();
            } catch (Exception e) {
                // Not a valid URL despite regex
            }
            this.urlDomain = domain;
            this.urlProtocol = protocol;
        } else if (detectedLang != null) {
            this.type = Type.CODE;
            this.codeLanguage = detectedLang;
            this.urlDomain = null;
            this.urlProtocol = null;
        } else {
            this.type = Type.TEXT;
            this.urlDomain = null;
            this.urlProtocol = null;
            this.codeLanguage = null;
        }
    }

    private String extractUrlFromShortcut(String text) {
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (line.trim().startsWith("URL=")) {
                return line.substring(line.indexOf("=") + 1).trim();
            }
        }
        return text;
    }

    private boolean isSVG(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim().toLowerCase();
        // Check for <svg tag. Might be preceded by <?xml...?>
        return trimmed.contains("<svg") && trimmed.endsWith("</svg>");
    }

    private boolean isURL(String text) {
        if (text == null || text.isBlank()) return false;
        // More inclusive URL regex: handles protocols, domains, paths, query params, and fragments
        // Including characters like (), [], ?, &, =, etc.
        String urlRegex = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w\\s.-]*)*(\\?[^\\s]*)?(#[^\\s]*)?/?$";
        // Actually, let's use a simpler but more robust approach: 
        // If it starts with http or contains :// and has no spaces, it's likely a URL
        String trimmed = text.trim();
        if (trimmed.contains(" ") || trimmed.contains("\n")) return false;
        
        return trimmed.matches("^(https?://)?[\\da-z.-]+\\.[a-z.]{2,6}.*$");
    }

    public String getUrlDomain() {
        return urlDomain;
    }

    public String getUrlProtocol() {
        return urlProtocol;
    }

    public String getCodeLanguage() {
        return codeLanguage;
    }

    public ClipboardItem(BufferedImage image) {
        this.type = Type.IMAGE;
        this.image = image;
        this.text = null;
        this.urlDomain = null;
        this.urlProtocol = null;
        this.codeLanguage = null;
        this.timestamp = LocalDateTime.now();
        this.sizeInBytes = (long) image.getWidth() * image.getHeight() * 4; // Approx size for RGBA
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public BufferedImage getImage() {
        return image;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getFormattedSize() {
        if (sizeInBytes < 1024)
            return sizeInBytes + " B";
        int exp = (int) (Math.log(sizeInBytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "B";
        return String.format("%.1f %s", sizeInBytes / Math.pow(1024, exp), pre);
    }

    public int getWordCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG && type != Type.CODE) || text == null || text.isBlank())
            return 0;
        return text.trim().split("\\s+").length;
    }

    public int getLineCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG && type != Type.CODE) || text == null || text.isEmpty())
            return 0;
        return (int) text.lines().count();
    }

    public int getCharacterCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG && type != Type.CODE) || text == null)
            return 0;
        return text.length();
    }

    public String getAspectRatio() {
        if (type != Type.IMAGE || width <= 0 || height <= 0)
            return "N/A";

        int common = gcd(width, height);
        return (width / common) + ":" + (height / common);
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public int getRows() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG || type == Type.CODE) {
            if (getCharacterCount() > 500 || getLineCount() > 10) return 2;
            return 1;
        } else {
            double ar = (double) width / height;
            if (ar < 0.5) return 2; // Portrait (updated threshold)
            if (width > 800 && height > 600) return 2; // Large
            return 1;
        }
    }

    public int getCols() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG || type == Type.CODE) {
            if (getCharacterCount() > 150 || getLineCount() > 4) return 2;
            return 1;
        } else {
            double ar = (double) width / height;
            if (ar > 4.0) return 2; // Panoramic (updated threshold)
            if (width > 800 && height > 600) return 2; // Large
            return 1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClipboardItem that = (ClipboardItem) o;
        if (type != that.type)
            return false;
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG || type == Type.CODE) {
            return Objects.equals(text, that.text);
        } else {
            // Simple check for images - compare dimensions and hash
            // (Note: BufferedImage hash is object-based, so this is still mostly identity)
            return width == that.width && height == that.height && image == that.image;
        }
    }

    @Override
    public int hashCode() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG || type == Type.CODE) {
            return Objects.hash(type, text);
        } else {
            // For images, hash is based on type and dimensions (since image itself is not hash-stable)
            return Objects.hash(type, width, height, image);
        }
    }

    public boolean contentEquals(ClipboardItem other) {
        if (other == null)
            return false;
        if (this.type != other.type)
            return false;
        if (this.type == Type.TEXT || this.type == Type.URL || this.type == Type.SVG || this.type == Type.CODE) {
            return this.text.equals(other.text);
        } else {
            // For images, we should ideally compare pixel data, but references might be
            // tricky.
            // In the monitor, we'll handle hash/check more carefully.
            return false; // Default to false for images for simple deep comparison
        }
    }
}
