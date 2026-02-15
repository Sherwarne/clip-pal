package com.virtualclipboard;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ClipboardMonitor {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Consumer<ClipboardItem> onNewItem;
    private Object lastContent = null;

    public ClipboardMonitor(Consumer<ClipboardItem> onNewItem) {
        this.onNewItem = onNewItem;
    }

    public void updateLastContent(Object content) {
        this.lastContent = content;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkClipboard, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void checkClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);

            if (contents == null)
                return;

            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);
                if (files != null && !files.isEmpty()) {
                    File file = files.get(0);
                    if (file.getName().toLowerCase().endsWith(".svg")) {
                        try {
                            String svgContent = Files.readString(file.toPath());
                            if (!svgContent.equals(lastContent)) {
                                lastContent = svgContent;
                                System.out.println("New SVG file detected");
                                onNewItem.accept(new ClipboardItem(svgContent));
                            }
                            return; // Priority given to SVG file over other flavors
                        } catch (IOException e) {
                            // Fallback to other flavors if reading fails
                        }
                    } else if (file.getName().toLowerCase().endsWith(".gif")) {
                        try {
                            byte[] gifBytes = Files.readAllBytes(file.toPath());
                            if (!(lastContent instanceof byte[]) || !java.util.Arrays.equals(gifBytes, (byte[]) lastContent)) {
                                lastContent = gifBytes;
                                System.out.println("New GIF file detected");
                                BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(gifBytes));
                                if (img != null) {
                                    onNewItem.accept(new ClipboardItem(gifBytes, img.getWidth(), img.getHeight()));
                                }
                            }
                            return;
                        } catch (IOException e) {
                            // Fallback
                        }
                    }
                }
            }

            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                if (!text.equals(lastContent)) {
                    lastContent = text;
                    System.out.println("New text item detected");
                    onNewItem.accept(new ClipboardItem(text));
                }
            } else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                BufferedImage image = (BufferedImage) contents.getTransferData(DataFlavor.imageFlavor);
                if (lastContent == null || !(lastContent instanceof BufferedImage)
                        || !imagesAreEqual(image, (BufferedImage) lastContent)) {
                    lastContent = image;
                    System.out.println("New image item detected");
                    onNewItem.accept(new ClipboardItem(image));
                }
            }
        } catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
            // Clipboard might be busy or flavor not available anymore
        }
    }

    private boolean imagesAreEqual(BufferedImage img1, BufferedImage img2) {
        if (img1 == img2)
            return true;
        if (img1 == null || img2 == null)
            return false;
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight())
            return false;

        // Fast check: sample some pixels first
        int w = img1.getWidth();
        int h = img1.getHeight();
        int[] points = { 0, 0, w / 2, h / 2, w - 1, h - 1, w / 4, h / 4 };
        for (int i = 0; i < points.length; i += 2) {
            if (img1.getRGB(points[i], points[i + 1]) != img2.getRGB(points[i], points[i + 1]))
                return false;
        }

        // Full check (optimized for performance)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y))
                    return false;
            }
        }
        return true;
    }

    public void stop() {
        scheduler.shutdown();
    }
}
