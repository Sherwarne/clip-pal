package com.virtualclipboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;

public class ClipboardItem implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Type {
        TEXT, IMAGE
    }

    private final Type type;
    private final String text;
    private transient BufferedImage image;
    private final LocalDateTime timestamp;
    private final long sizeInBytes;
    private final int width;
    private final int height;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (type == Type.IMAGE && image != null) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            out.writeInt(imageBytes.length);
            out.write(imageBytes);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (type == Type.IMAGE) {
            int length = in.readInt();
            byte[] imageBytes = new byte[length];
            in.readFully(imageBytes);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
            image = ImageIO.read(bais);
        }
    }

    public ClipboardItem(String text) {
        this.type = Type.TEXT;
        this.text = text;
        this.image = null;
        this.timestamp = LocalDateTime.now();
        this.sizeInBytes = text.getBytes().length;
        this.width = -1;
        this.height = -1;
    }

    public ClipboardItem(BufferedImage image) {
        this.type = Type.IMAGE;
        this.image = image;
        this.text = null;
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
        if (type != Type.TEXT || text == null || text.isBlank())
            return 0;
        return text.trim().split("\\s+").length;
    }

    public int getLineCount() {
        if (type != Type.TEXT || text == null || text.isEmpty())
            return 0;
        return (int) text.lines().count();
    }

    public int getCharacterCount() {
        if (type != Type.TEXT || text == null)
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
        if (type == Type.TEXT) {
            if (getCharacterCount() > 500 || getLineCount() > 10) return 2; // Even longer
            return 1;
        } else {
            if (width > 1200 || height > 1000) return 2; // Very big
            if (height > width * 1.5) return 2; // Taller
            return 1;
        }
    }

    public int getCols() {
        if (type == Type.TEXT) {
            if (getCharacterCount() > 500 || getLineCount() > 10) return 2; // Even longer
            if (getCharacterCount() > 150 || getLineCount() > 4) return 2; // Longer
            return 1;
        } else {
            if (width > 1200 || height > 1000) return 2; // Very big
            if (width > height * 1.5) return 2; // Wider
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
        if (type == Type.TEXT) {
            return text.equals(that.text);
        } else {
            // Simple check for images - compare dimensions or hash (though bufferedImage
            // hash is object-based)
            // For now, let's just use object equality for the monitor logic
            return image == that.image;
        }
    }

    public boolean contentEquals(ClipboardItem other) {
        if (other == null)
            return false;
        if (this.type != other.type)
            return false;
        if (this.type == Type.TEXT) {
            return this.text.equals(other.text);
        } else {
            // For images, we should ideally compare pixel data, but references might be
            // tricky.
            // In the monitor, we'll handle hash/check more carefully.
            return false; // Default to false for images for simple deep comparison
        }
    }
}
