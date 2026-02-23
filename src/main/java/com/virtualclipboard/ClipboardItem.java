package com.virtualclipboard;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Objects;

public class ClipboardItem implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Type {
        TEXT, IMAGE, URL, SVG, GIF
    }

    private final Type type;
    private final String text;
    private String urlDomain;
    private String urlProtocol;
    private transient BufferedImage image;
    private byte[] gifData;
    private final LocalDateTime timestamp;
    private final long sizeInBytes;
    private final int width;
    private final int height;
    private int frameCount = 0;
    private int durationMs = 0;

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
        } else if (type == Type.GIF) {
            // Always re-parse metadata on load to ensure accuracy/updates
            parseGifMetadata();
        } else if (type == Type.URL) {
            // Recalculate URL metadata
            String domain = "N/A";
            String protocol = "N/A";
            try {
                String spec = text.startsWith("http") ? text : "http://" + text;
                URL url = new URI(spec).toURL();
                domain = url.getHost();
                protocol = url.getProtocol();
            } catch (Exception e) {
                // Not a valid URL despite regex
            }
            this.urlDomain = domain;
            this.urlProtocol = protocol;
        }
    }

    public ClipboardItem(byte[] gifData, int width, int height) {
        this.type = Type.GIF;
        this.gifData = gifData;
        this.image = null;
        this.text = null;
        this.urlDomain = null;
        this.urlProtocol = null;
        this.timestamp = LocalDateTime.now();
        this.sizeInBytes = gifData.length;
        this.width = width;
        this.height = height;
        parseGifMetadata();
    }

    private void parseGifMetadata() {
        if (gifData == null) return;
        
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gifData))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(iis);
                this.frameCount = reader.getNumImages(true);
                
                int totalDuration = 0;
                for (int i = 0; i < frameCount; i++) {
                    IIOMetadata metadata = reader.getImageMetadata(i);
                    String metaFormatName = metadata.getNativeMetadataFormatName();
                    IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
                    
                    int delay = 2; // Default 20ms (2 * 10ms)
                    
                    NodeList children = root.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node node = children.item(j);
                        if ("GraphicControlExtension".equals(node.getNodeName())) {
                            NamedNodeMap attrs = node.getAttributes();
                            Node delayNode = attrs.getNamedItem("delayTime");
                            if (delayNode != null) {
                                delay = Integer.parseInt(delayNode.getNodeValue());
                                // Many viewers treat 0 delay as 20ms (2cs)
                                if (delay == 0) {
                                    delay = 2;
                                }
                            }
                            break;
                        }
                    }
                    // delayTime is in hundredths of a second (10ms)
                    totalDuration += delay * 10;
                }
                this.durationMs = totalDuration;
            }
        } catch (Exception e) {
            // Ignore metadata parsing errors, basic info is still valid
            System.err.println("Failed to parse GIF metadata: " + e.getMessage());
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

        if (isSVG(processedText)) {
            this.type = Type.SVG;
            this.urlDomain = null;
            this.urlProtocol = null;
        } else if (isURL(processedText)) {
            this.type = Type.URL;
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
        } else {
            this.type = Type.TEXT;
            this.urlDomain = null;
            this.urlProtocol = null;
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
        String trimmed = text.trim();
        
        // Fast check: must start with <svg or <?xml and contain <svg
        if (!trimmed.toLowerCase().contains("<svg")) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Disable validation and features that might trigger network access
            factory.setValidating(false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Wrap in try-catch to handle potential parsing errors
            InputSource is = new InputSource(new StringReader(trimmed));
            Document doc = builder.parse(is);
            
            // Check if root element is svg
            if (doc.getDocumentElement() != null && 
                "svg".equalsIgnoreCase(doc.getDocumentElement().getNodeName())) {
                return true;
            }
        } catch (Exception e) {
            // Parsing failed, not a valid SVG
            return false;
        }
        
        return false;
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

    public ClipboardItem(BufferedImage image) {
        this.type = Type.IMAGE;
        this.image = image;
        this.text = null;
        this.urlDomain = null;
        this.urlProtocol = null;
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

    public byte[] getGifData() {
        return gifData;
    }

    public BufferedImage getAsImage() {
        if (type == Type.IMAGE) return image;
        if (type == Type.GIF && gifData != null) {
            try {
                return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(gifData));
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return null;
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

    public int getFrameCount() {
        return frameCount;
    }

    public int getDurationMs() {
        return durationMs;
    }
    
    public String getFormattedDuration() {
        if (durationMs < 1000) {
            return durationMs + " ms";
        } else {
            return String.format("%.1f s", durationMs / 1000.0);
        }
    }

    public String getFormattedSize() {
        if (sizeInBytes < 1024)
            return sizeInBytes + " B";
        int exp = (int) (Math.log(sizeInBytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "B";
        return String.format("%.1f %s", sizeInBytes / Math.pow(1024, exp), pre);
    }

    public int getWordCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG) || text == null || text.isBlank())
            return 0;
        return text.trim().split("\\s+").length;
    }

    public int getLineCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG) || text == null || text.isEmpty())
            return 0;
        return (int) text.lines().count();
    }

    public int getCharacterCount() {
        if ((type != Type.TEXT && type != Type.URL && type != Type.SVG) || text == null)
            return 0;
        return text.length();
    }

    public String getAspectRatio() {
        if ((type != Type.IMAGE && type != Type.GIF) || width <= 0 || height <= 0)
            return "N/A";

        int common = gcd(width, height);
        return (width / common) + ":" + (height / common);
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public int getRows() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG) {
            if (getCharacterCount() > 500 || getLineCount() > 10) return 2;
            return 1;
        } else {
            double ar = (double) width / height;
            if (ar < 0.8) return 2; // Portrait (updated threshold)
            if (width > 800 && height > 600) return 2; // Large
            return 1;
        }
    }

    public int getCols() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG) {
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
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG) {
            return Objects.equals(text, that.text);
        } else if (type == Type.GIF) {
            return width == that.width && height == that.height && java.util.Arrays.equals(gifData, that.gifData);
        } else {
            // Simple check for images - compare dimensions and hash
            // (Note: BufferedImage hash is object-based, so this is still mostly identity)
            return width == that.width && height == that.height && image == that.image;
        }
    }

    @Override
    public int hashCode() {
        if (type == Type.TEXT || type == Type.URL || type == Type.SVG) {
            return Objects.hash(type, text);
        } else if (type == Type.GIF) {
            return Objects.hash(type, width, height, java.util.Arrays.hashCode(gifData));
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
        if (this.type == Type.TEXT || this.type == Type.URL || this.type == Type.SVG) {
            return this.text.equals(other.text);
        } else {
            // For images, we should ideally compare pixel data, but references might be
            // tricky.
            // In the monitor, we'll handle hash/check more carefully.
            return false; // Default to false for images for simple deep comparison
        }
    }
}
