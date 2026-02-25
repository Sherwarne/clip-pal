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
    private String caption;
    private transient BufferedImage image;
    private byte[] gifData;
    private final LocalDateTime timestamp;
    private final long sizeInBytes;
    private final int width;
    private final int height;
    private int frameCount = 0;
    private int durationMs = 0;

    public ClipboardItem(String text) {
        this.text = text;
        this.image = null;
        this.gifData = null;
        this.width = 0;
        this.height = 0;
        this.timestamp = LocalDateTime.now();
        this.sizeInBytes = text == null ? 0 : text.getBytes().length;
        
        if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
            this.type = Type.URL;
            try {
                URL url = new URI(text).toURL();
                this.urlDomain = url.getHost();
                this.urlProtocol = url.getProtocol();
            } catch (Exception e) {
                this.urlDomain = "N/A";
                this.urlProtocol = "N/A";
            }
        } else if (text != null && text.trim().startsWith("<svg") && text.trim().endsWith("</svg>")) {
            this.type = Type.SVG;
        } else {
            this.type = Type.TEXT;
        }
    }

    public ClipboardItem(BufferedImage image) {
        this.type = Type.IMAGE;
        this.image = image;
        this.text = null;
        this.timestamp = LocalDateTime.now();
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.sizeInBytes = 0; // Placeholder
        this.urlDomain = null;
        this.urlProtocol = null;
        this.gifData = null;
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
            parseGifMetadata();
        } else if (type == Type.URL) {
            String domain = "N/A";
            String protocol = "N/A";
            try {
                String spec = text.startsWith("http") ? text : "http://" + text;
                URL url = new URI(spec).toURL();
                domain = url.getHost();
                protocol = url.getProtocol();
            } catch (Exception e) {
            }
            this.urlDomain = domain;
            this.urlProtocol = protocol;
        }
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
                    
                    int delay = 2; // Default 20ms
                    
                    NodeList children = root.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node node = children.item(j);
                        if ("GraphicControlExtension".equals(node.getNodeName())) {
                            NamedNodeMap attrs = node.getAttributes();
                            Node delayNode = attrs.getNamedItem("delayTime");
                            if (delayNode != null) {
                                delay = Integer.parseInt(delayNode.getNodeValue());
                                if (delay == 0) delay = 2;
                            }
                            break;
                        }
                    }
                    totalDuration += delay * 10;
                }
                this.durationMs = totalDuration;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Type getType() { return type; }
    public String getText() { return text; }
    public String getUrlDomain() { return urlDomain; }
    public String getUrlProtocol() { return urlProtocol; }
    public BufferedImage getImage() { return image; }
    public byte[] getGifData() { return gifData; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public long getSizeInBytes() { return sizeInBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFrameCount() { return frameCount; }
    public int getDurationMs() { return durationMs; }
    
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getFormattedSize() {
        if (sizeInBytes < 1024) return sizeInBytes + " B";
        if (sizeInBytes < 1024 * 1024) return String.format("%.1f KB", sizeInBytes / 1024.0);
        return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
    }

    public int getCharacterCount() {
        return text == null ? 0 : text.length();
    }

    public int getWordCount() {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }

    public int getLineCount() {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\r\n|\r|\n").length;
    }

    public BufferedImage getAsImage() {
        if (type == Type.IMAGE) return image;
        if (type == Type.GIF && gifData != null) {
            try {
                return ImageIO.read(new ByteArrayInputStream(gifData));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public int getCols() {
        if (type == Type.IMAGE || type == Type.GIF || type == Type.SVG) {
             if (width > height * 1.5) return 2;
             return 1;
        }
        if (text != null && text.length() > 200) return 2;
        return 1;
    }

    public int getRows() {
         if (type == Type.IMAGE || type == Type.GIF || type == Type.SVG) {
             if (height > width * 1.5) return 2;
             return 1;
        }
        if (text != null && text.length() > 400) return 2;
        return 1;
    }

    public String getAspectRatio() {
        if (width == 0 || height == 0) return "N/A";
        int gcd = gcd(width, height);
        return (width / gcd) + ":" + (height / gcd);
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public String getFormattedDuration() {
        if (durationMs < 1000) return durationMs + " ms";
        return String.format("%.1f s", durationMs / 1000.0);
    }
}
