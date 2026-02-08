package com.virtualclipboard;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.ArrayList;
import java.util.List;

public class App extends JFrame {
    private final JPanel contentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 15, 15));
    private final JScrollPane scrollPane = new JScrollPane(contentPanel);
    private final ClipboardMonitor monitor;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final List<ClipboardItem> items = new ArrayList<>();
    private OcrService ocrService;

    public App() {
        ocrService = new OcrService();
        setTitle("Virtual Clipboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 750);
        setMinimumSize(new Dimension(500, 600));
        setLocationRelativeTo(null);

        Color bgMain = new Color(18, 18, 20);

        setLayout(new BorderLayout());
        getContentPane().setBackground(bgMain);

        // Header Section
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(40, 30, 20, 30));

        JLabel titleLabel = new JLabel("Clipboard");
        titleLabel.setFont(new Font("Segoe UI Variable Display Semibold", Font.PLAIN, 42));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        FlatSVGIcon trashIcon = new FlatSVGIcon("com/virtualclipboard/icons/trashcan.svg", 24, 24);
        trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));

        JButton clearButton = new JButton(trashIcon);
        clearButton.setToolTipText("Clear History");
        clearButton.setOpaque(false);
        clearButton.setContentAreaFilled(false);
        clearButton.setFocusPainted(false);
        clearButton.setBorder(null);
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        clearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(255, 100, 100)));
                clearButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));
                clearButton.repaint();
            }
        });

        clearButton.addActionListener(e -> animateClear());
        headerPanel.add(clearButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        contentPanel.setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);
        add(scrollPane, BorderLayout.CENTER);

        // Adaptive resize handling
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshUI();
            }
        });

        monitor = new ClipboardMonitor(item -> SwingUtilities.invokeLater(() -> {
            items.add(0, item);
            animateNewEntry(item);
        }));
    }

    private void animateNewEntry(ClipboardItem item) {
        AnimatedCard card = createItemCard(item);
        card.setAlpha(0.0f);
        contentPanel.add(card, 0);
        contentPanel.revalidate();

        Timer timer = new Timer(10, null);
        final float[] alpha = { 0.0f };
        timer.addActionListener(e -> {
            alpha[0] += 0.05f;
            if (alpha[0] >= 1.0f) {
                alpha[0] = 1.0f;
                timer.stop();
            }
            card.setAlpha(alpha[0]);
            card.repaint();
        });
        timer.start();
    }

    private void refreshUI() {
        contentPanel.removeAll();
        for (ClipboardItem item : items) {
            contentPanel.add(createItemCard(item));
        }
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void animateClear() {
        Component[] components = contentPanel.getComponents();
        if (components.length == 0)
            return;

        Timer timer = new Timer(10, null);
        final float[] alpha = { 1.0f };
        timer.addActionListener(e -> {
            alpha[0] -= 0.1f;
            if (alpha[0] <= 0.0f) {
                alpha[0] = 0.0f;
                timer.stop();
                items.clear();
                refreshUI();
            }
            for (Component c : components) {
                if (c instanceof AnimatedCard) {
                    ((AnimatedCard) c).setAlpha(alpha[0]);
                }
            }
            contentPanel.repaint();
        });
        timer.start();
    }

    private AnimatedCard createItemCard(ClipboardItem item) {
        int windowWidth = getWidth();
        int cardWidth;

        if (windowWidth > 1300) {
            cardWidth = (windowWidth - 140) / 4;
        } else if (windowWidth > 900) {
            cardWidth = (windowWidth - 110) / 3;
        } else if (windowWidth > 600) {
            cardWidth = (windowWidth - 80) / 2;
        } else {
            cardWidth = windowWidth - 60;
        }

        AnimatedCard card = new AnimatedCard(new BorderLayout(15, 10));
        card.setPreferredSize(new Dimension(cardWidth, 160));
        card.setBorder(new EmptyBorder(15, 20, 20, 20));

        // Header Section (Time + Controls)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel time = new JLabel(item.getTimestamp().format(formatter));
        time.setFont(new Font("Montserrat", Font.PLAIN, 15));
        time.setForeground(new Color(110, 110, 125));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlPanel.setOpaque(false);

        JButton infoBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/info.svg", 16, 16));
        JButton deleteBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/close.svg", 16, 16));

        infoBtn.addActionListener(e -> showInfoPopup(item));
        deleteBtn.addActionListener(e -> deleteEntry(item, card));

        controlPanel.add(infoBtn);
        controlPanel.add(Box.createHorizontalStrut(12));
        controlPanel.add(deleteBtn);

        headerPanel.add(time, BorderLayout.WEST);
        headerPanel.add(controlPanel, BorderLayout.EAST);
        card.add(headerPanel, BorderLayout.NORTH);

        // Content Area
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        JLabel preview = new JLabel();
        preview.setForeground(Color.WHITE);
        preview.setFont(new Font("Roboto", Font.BOLD, 22));

        if (item.getType() == ClipboardItem.Type.TEXT) {
            String text = item.getText().trim();
            if (text.length() > 100)
                text = text.substring(0, 97) + "...";
            preview.setText("<html><body style='width: " + (cardWidth - 60) + "px'>"
                    + text.replace("<", "&lt;").replace("\n", " ") + "</body></html>");
        } else {
            Image scaled = item.getImage().getScaledInstance(-1, 80, Image.SCALE_SMOOTH);
            preview.setIcon(new ImageIcon(scaled));
        }
        contentArea.add(preview, BorderLayout.WEST);
        card.add(contentArea, BorderLayout.CENTER);

        // Type Indicator (Bottom Right)
        JLabel typeIndicator = new JLabel(item.getType() == ClipboardItem.Type.TEXT ? "T" : "I");
        typeIndicator.setFont(new Font("Montserrat", Font.BOLD, 17));
        typeIndicator.setForeground(new Color(110, 110, 125, 150));

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.add(typeIndicator, BorderLayout.EAST);
        card.add(footerPanel, BorderLayout.SOUTH);

        // Premium Interactions
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setHovered(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setHovered(false);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                copyToSystemClipboard(item);
                animateCopyFeedback(card, time, item);
            }
        });

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return card;
    }

    private void animateCopyFeedback(AnimatedCard card, JLabel timeLabel, ClipboardItem item) {
        card.cancelTimers();

        timeLabel.setText("✨ Copied to clipboard");
        timeLabel.setForeground(Color.WHITE);

        final String originalTimestamp = item.getTimestamp().format(formatter);

        Timer pulseTimer = new Timer(10, null);
        final int[] frame = { 0 };
        pulseTimer.addActionListener(e -> {
            frame[0]++;
            if (frame[0] <= 15) {
                float ratio = frame[0] / 15.0f;
                card.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(Color.WHITE, (int) (2 + (5 * (1 - ratio))), true),
                        new EmptyBorder(12, 19, 19, 19)));
            } else {
                pulseTimer.stop();
                Timer resetTimer = new Timer(1200, evt -> {
                    timeLabel.setText(originalTimestamp);
                    timeLabel.setForeground(new Color(110, 110, 125));
                });
                resetTimer.setRepeats(false);
                card.setResetTimer(resetTimer);
                resetTimer.start();
            }
            card.repaint();
        });

        card.setPulseTimer(pulseTimer);
        pulseTimer.start();
    }

    private JButton createSubtleButton(Icon icon) {
        JButton btn = new JButton(icon);
        if (icon instanceof FlatSVGIcon) {
            ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));
        }
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorder(null);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (icon instanceof FlatSVGIcon) {
                    ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> Color.WHITE));
                    btn.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (icon instanceof FlatSVGIcon) {
                    ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));
                    btn.repaint();
                }
            }
        });

        return btn;
    }

    private void deleteEntry(ClipboardItem item, AnimatedCard card) {
        Timer timer = new Timer(10, null);
        final float[] alpha = { 1.0f };
        timer.addActionListener(e -> {
            alpha[0] -= 0.1f;
            if (alpha[0] <= 0.0f) {
                timer.stop();
                items.remove(item);
                refreshUI();
            }
            card.setAlpha(alpha[0]);
            card.repaint();
        });
        timer.start();
    }

    private void showInfoPopup(ClipboardItem item) {
        JDialog dialog = new JDialog(this, "Item Information", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.getContentPane().setBackground(new Color(18, 18, 20));

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Metadata Panel
        JPanel metaPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        metaPanel.setOpaque(false);

        List<String[]> details = new ArrayList<>();
        details.add(new String[] { "Created", item.getTimestamp().format(formatter) });
        details.add(new String[] { "Type", item.getType().toString() });
        details.add(new String[] { "Size", item.getFormattedSize() });

        if (item.getType() == ClipboardItem.Type.TEXT) {
            details.add(new String[] { "Characters", String.valueOf(item.getCharacterCount()) });
            details.add(new String[] { "Words", String.valueOf(item.getWordCount()) });
            details.add(new String[] { "Lines", String.valueOf(item.getLineCount()) });
        } else {
            details.add(new String[] { "Dimensions", item.getWidth() + " x " + item.getHeight() });
            details.add(new String[] { "Aspect Ratio", item.getAspectRatio() });
        }

        for (String[] detail : details) {
            JLabel key = new JLabel(detail[0]);
            key.setForeground(new Color(110, 110, 125));
            key.setFont(new Font("Segoe UI Variable Small", Font.BOLD, 14));

            JLabel val = new JLabel(detail[1]);
            val.setForeground(Color.WHITE);
            val.setFont(new Font("Segoe UI Variable Text", Font.PLAIN, 14));

            metaPanel.add(key);
            metaPanel.add(val);
        }

        mainPanel.add(metaPanel, BorderLayout.NORTH);

        // Preview
        if (item.getType() == ClipboardItem.Type.TEXT) {
            JTextArea textArea = new JTextArea(item.getText());
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(new Color(28, 28, 32));
            textArea.setForeground(Color.WHITE);
            textArea.setFont(new Font("Segoe UI Variable Text", Font.PLAIN, 16));
            textArea.setCaretColor(Color.WHITE);
            textArea.setBorder(new EmptyBorder(15, 15, 15, 15));

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(500, 300));
            scroll.setBorder(new LineBorder(new Color(45, 45, 52)));
            mainPanel.add(scroll, BorderLayout.CENTER);
        } else {
            JPanel imageContainer = new JPanel(new BorderLayout(10, 10));
            imageContainer.setOpaque(false);

            JLabel imgLabel = new JLabel(new ImageIcon(item.getImage().getScaledInstance(
                    item.getWidth() > 500 ? 500 : -1,
                    -1,
                    Image.SCALE_SMOOTH)));
            imgLabel.setHorizontalAlignment(JLabel.CENTER);
            imageContainer.add(imgLabel, BorderLayout.CENTER);

            // OCR Action Panel
            JPanel ocrPanel = new JPanel(new BorderLayout(10, 10));
            ocrPanel.setOpaque(false);
            ocrPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

            JButton scanBtn = new JButton("Scan Text (OCR)");
            scanBtn.setBackground(new Color(45, 45, 52));
            scanBtn.setForeground(Color.WHITE);
            scanBtn.setFocusPainted(false);
            scanBtn.setBorder(new EmptyBorder(8, 15, 8, 15));

            JTextArea resultArea = new JTextArea();
            resultArea.setEditable(false);
            resultArea.setLineWrap(true);
            resultArea.setWrapStyleWord(true);
            resultArea.setBackground(new Color(28, 28, 32));
            resultArea.setForeground(Color.WHITE);
            resultArea.setFont(new Font("Segoe UI Variable Text", Font.PLAIN, 14));
            resultArea.setBorder(new EmptyBorder(10, 10, 10, 10));
            resultArea.setVisible(false);

            JScrollPane resultScroll = new JScrollPane(resultArea);
            resultScroll.setPreferredSize(new Dimension(500, 150));
            resultScroll.setBorder(new LineBorder(new Color(45, 45, 52)));
            resultScroll.setVisible(false);

            scanBtn.addActionListener(e -> {
                scanBtn.setEnabled(false);
                scanBtn.setText("Scanning...");

                new SwingWorker<String, Void>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        return ocrService.extractText(item.getImage());
                    }

                    @Override
                    protected void done() {
                        try {
                            String text = get();
                            if (text == null || text.trim().isEmpty()) {
                                resultArea.setText("No text detected.");
                            } else {
                                resultArea.setText(text.trim());
                                resultScroll.setVisible(true);
                                resultArea.setVisible(true);

                                // Add copy button for OCR result
                                JButton copyOcrBtn = new JButton("Copy Extracted Text");
                                copyOcrBtn.setBackground(new Color(60, 60, 70));
                                copyOcrBtn.setForeground(Color.WHITE);
                                copyOcrBtn.setFocusPainted(false);
                                copyOcrBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
                                copyOcrBtn.addActionListener(copyEvt -> {
                                    StringSelection selection = new StringSelection(text.trim());
                                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                                    copyOcrBtn.setText("✓ Copied!");
                                });
                                ocrPanel.add(copyOcrBtn, BorderLayout.SOUTH);

                                dialog.pack();
                                dialog.setLocationRelativeTo(App.this);
                            }
                            scanBtn.setText("Extraction Complete");
                        } catch (Exception ex) {
                            resultArea.setText("OCR Error: " + ex.getMessage());
                            resultArea.setVisible(true);
                        }
                    }
                }.execute();
            });

            ocrPanel.add(scanBtn, BorderLayout.NORTH);
            ocrPanel.add(resultScroll, BorderLayout.CENTER);

            imageContainer.add(ocrPanel, BorderLayout.SOUTH);
            mainPanel.add(imageContainer, BorderLayout.CENTER);
        }

        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(Color.WHITE);
        closeBtn.setForeground(new Color(18, 18, 20));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(closeBtn);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // Custom Component for Opacity Support
    private static class AnimatedCard extends JPanel {
        private float alpha = 1.0f;
        private boolean hovered = false;
        private Timer pulseTimer;
        private Timer resetTimer;

        public AnimatedCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        public void setHovered(boolean hovered) {
            this.hovered = hovered;
            repaint();
        }

        public void setPulseTimer(Timer t) {
            this.pulseTimer = t;
        }

        public void setResetTimer(Timer t) {
            this.resetTimer = t;
        }

        public void cancelTimers() {
            if (pulseTimer != null && pulseTimer.isRunning())
                pulseTimer.stop();
            if (resetTimer != null && resetTimer.isRunning())
                resetTimer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color body = new Color(0x222226);
            Color topLeft = new Color(0x29292D);
            Color bottomRight = new Color(0x1C1C20);
            Color accent = Color.WHITE;

            // Main body
            g2.setColor(body);
            g2.fillRect(0, 0, w, h);

            if (hovered) {
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(3));
                g2.drawRect(1, 1, w - 3, h - 3);
            } else {
                // Top-left shading (three pixels thick)
                g2.setColor(topLeft);
                g2.fillRect(0, 0, w, 3); // Top line
                g2.fillRect(0, 0, 3, h); // Left line

                // Bottom-right shading (three pixels thick)
                g2.setColor(bottomRight);
                g2.fillRect(0, h - 3, w, 3); // Bottom line
                g2.fillRect(w - 3, 0, 3, h); // Right line
            }

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paint(g2);
            g2.dispose();
        }
    }

    private void copyToSystemClipboard(ClipboardItem item) {
        if (item.getType() == ClipboardItem.Type.TEXT) {
            String text = item.getText();
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            monitor.updateLastContent(text);
        } else if (item.getType() == ClipboardItem.Type.IMAGE) {
            BufferedImage image = item.getImage();
            Transferable imageTransferable = new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { DataFlavor.imageFlavor };
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    if (isDataFlavorSupported(flavor))
                        return image;
                    throw new UnsupportedFlavorException(flavor);
                }
            };
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(imageTransferable, null);
            monitor.updateLastContent(image);
        }
    }

    public void start() {
        monitor.start();
        setVisible(true);
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.start();
        });
    }
}

/**
 * A FlowLayout extension that wraps components to the next line
 * Correctly calculates preferred size based on available width.
 */
class WrapLayout extends FlowLayout {
    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            if (targetWidth == 0)
                targetWidth = Integer.MAX_VALUE;

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0)
                        rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                dim.width -= (hgap + 1);
            }

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0)
            dim.height += getVgap();
        dim.height += rowHeight;
    }
}
