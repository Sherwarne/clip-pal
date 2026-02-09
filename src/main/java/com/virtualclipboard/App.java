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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.ArrayList;
import java.util.List;

public class App extends JFrame {
    private final JPanel contentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 15, 15));
    private final JScrollPane scrollPane = new JScrollPane(contentPanel);
    private final ClipboardMonitor monitor;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Tabbed Structure
    private static class ClipboardTab implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        List<ClipboardItem> items = new ArrayList<>();

        public ClipboardTab(String name) {
            this.name = name;
        }
    }

    private static final String CLIPBOARD_STATE_FILE = "clipboard_state.dat";

    private final List<ClipboardTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private JPanel tabsPanel; // UI Container for tabs

    private OcrService ocrService;
    private ConfigManager configManager = new ConfigManager();

    // Sleek Modern Fonts
    private static final String FONT_FAMILY = "Segoe UI Variable Display";
    private static final String FONT_FAMILY_TEXT = "Segoe UI Variable Text";

    // Helper to get font with fallback
    private Font getAppFont(String name, int style, float size) {
        return new Font(name, style, (int) size);
    }

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
        titleLabel.setFont(getAppFont(FONT_FAMILY, Font.BOLD, 42)); // Use sleek font
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // Tab Bar UI
        // Tab Bar UI
        tabsPanel = new JPanel(new GridBagLayout()); // Use GridBagLayout for dynamic sizing
        tabsPanel.setOpaque(false);
        tabs.add(new ClipboardTab("Main")); // Default tab

        // Try to restore previous clipboard state (tabs and items)
        loadClipboardState();
        refreshTabsUI();

        JPanel centerHeader = new JPanel(new BorderLayout());
        centerHeader.setOpaque(false);
        centerHeader.add(tabsPanel, BorderLayout.SOUTH);
        centerHeader.setBorder(new EmptyBorder(0, 20, 0, 0));

        headerPanel.add(centerHeader, BorderLayout.CENTER);

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

        clearButton.addActionListener(e -> {
            showConfirmationDialog("Clear History",
                    "Are you sure you want to clear all history?\nThis action cannot be undone.", this::animateClear);
        });

        FlatSVGIcon settingsIcon = new FlatSVGIcon("com/virtualclipboard/icons/settings.svg", 24, 24);
        settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));
        JButton settingsButton = new JButton(settingsIcon);
        settingsButton.setToolTipText("Settings");
        settingsButton.setOpaque(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setFocusPainted(false);
        settingsButton.setBorder(null);
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> Color.WHITE));
                settingsButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(110, 110, 125)));
                settingsButton.repaint();
            }
        });
        settingsButton.addActionListener(e -> showSettingsPopup());

        JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightHeaderPanel.setOpaque(false);
        rightHeaderPanel.add(settingsButton);
        rightHeaderPanel.add(clearButton);

        headerPanel.add(rightHeaderPanel, BorderLayout.EAST);

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
            getCurrentTab().items.add(0, item);
            refreshUI();
            if (tabs.indexOf(getCurrentTab()) == activeTabIndex) {
                animateNewEntry(item);
            }
        }));

        // Save clipboard state when the window is closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveClipboardState();
            }
        });
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

    private ClipboardTab getCurrentTab() {
        if (tabs.isEmpty())
            tabs.add(new ClipboardTab("Main"));
        if (activeTabIndex >= tabs.size())
            activeTabIndex = 0;
        return tabs.get(activeTabIndex);
    }

    private void refreshTabsUI() {
        tabsPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 2, 0, 2);

        for (int i = 0; i < tabs.size(); i++) {
            ClipboardTab tab = tabs.get(i);
            boolean isActive = (i == activeTabIndex);

            // Tab Button
            JButton tabBtn = new JButton(tab.name);
            tabBtn.setFont(getAppFont(FONT_FAMILY_TEXT, isActive ? Font.BOLD : Font.PLAIN, 14));
            tabBtn.setForeground(isActive ? Color.WHITE : new Color(110, 110, 125));
            tabBtn.setBackground(isActive ? new Color(45, 45, 52) : new Color(0, 0, 0, 0));
            tabBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
            tabBtn.setFocusPainted(false);
            tabBtn.setContentAreaFilled(isActive);
            tabBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            int finalI = i;
            tabBtn.addActionListener(e -> {
                if (activeTabIndex != finalI) {
                    activeTabIndex = finalI;
                    refreshTabsUI();
                    refreshUI();
                }
            });

            // Context Menu for Tab
            JPopupMenu tabMenu = new JPopupMenu();

            JMenuItem renameItem = new JMenuItem("Rename Tab");
            renameItem.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(this, "Enter new tab name:", tab.name);
                if (newName != null && !newName.trim().isEmpty()) {
                    tab.name = newName.trim();
                    refreshTabsUI();
                }
            });
            tabMenu.add(renameItem);

            if (tabs.size() > 1) {
                JMenuItem deleteItem = new JMenuItem("Delete Tab");
                deleteItem.addActionListener(e -> {
                    showConfirmationDialog("Delete Tab", "Delete tab '" + tab.name + "' and its items?", () -> {
                        tabs.remove(tab);
                        if (activeTabIndex >= tabs.size())
                            activeTabIndex = 0;
                        refreshTabsUI();
                        refreshUI();
                    });
                });
                tabMenu.add(deleteItem);
            }

            tabBtn.setComponentPopupMenu(tabMenu);

            // Drag and Drop Logic
            MouseAdapter dragHandler = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!isActive) {
                        tabBtn.setBackground(new Color(45, 45, 52, 100)); // Subtle hover
                        tabBtn.setContentAreaFilled(true);
                        tabBtn.repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!isActive) {
                        tabBtn.setBackground(new Color(0, 0, 0, 0));
                        tabBtn.setContentAreaFilled(false);
                        tabBtn.repaint();
                    }
                }

                // Only start drag if moved
                public void mouseDragged(MouseEvent e) {
                    JComponent c = (JComponent) e.getSource();
                    TransferHandler handler = c.getTransferHandler();
                    handler.exportAsDrag(c, e, TransferHandler.MOVE);
                }
            };
            tabBtn.addMouseListener(dragHandler);
            tabBtn.addMouseMotionListener(dragHandler);

            tabBtn.setTransferHandler(new TransferHandler("text") {
                @Override
                public int getSourceActions(JComponent c) {
                    return MOVE;
                }

                @Override
                protected Transferable createTransferable(JComponent c) {
                    return new StringSelection(String.valueOf(finalI)); // Transfer index
                }

                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.stringFlavor);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    try {
                        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        int sourceIndex = Integer.parseInt(data);
                        int targetIndex = finalI;

                        if (sourceIndex != targetIndex) {
                            ClipboardTab draggedTab = tabs.remove(sourceIndex);
                            tabs.add(targetIndex, draggedTab);

                            // Adjust active index if needed
                            if (activeTabIndex == sourceIndex) {
                                activeTabIndex = targetIndex;
                            } else if (activeTabIndex == targetIndex) {
                                // If we dropped onto the active tab, it shifts
                                if (sourceIndex < targetIndex)
                                    activeTabIndex--;
                                else
                                    activeTabIndex++;
                            } else {
                                // Complex shifting logic, simpliest is to adhere to 'draggedTab' reference
                                activeTabIndex = tabs.indexOf(getCurrentTab());
                            }

                            // Ensure valid index just in case
                            activeTabIndex = tabs.indexOf(getCurrentTab());

                            refreshTabsUI();
                            return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });

            tabsPanel.add(tabBtn, gbc);
        }

        if (tabs.size() < 7) { // Increased limit to 7
            JButton addBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/plus.svg", 16, 16));
            addBtn.setToolTipText("New Tab");
            addBtn.addActionListener(e -> {
                tabs.add(new ClipboardTab("Tab " + (tabs.size() + 1)));
                activeTabIndex = tabs.size() - 1; // Switch to new tab
                refreshTabsUI();
                refreshUI();
            });

            gbc.weightx = 0; // Don't let add button stretch
            tabsPanel.add(addBtn, gbc);
        }

        tabsPanel.revalidate();
        tabsPanel.repaint();
    }

    private void refreshUI() {
        contentPanel.removeAll();
        ClipboardTab current = getCurrentTab();
        for (ClipboardItem item : current.items) {
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
                getCurrentTab().items.clear(); // Fix items.clear()
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

    /**
     * Persist the current clipboard tabs and items to disk so they can be
     * restored on next launch.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void saveClipboardState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CLIPBOARD_STATE_FILE))) {
            oos.writeObject(tabs);
            oos.writeInt(activeTabIndex);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Restore previously saved clipboard tabs and items from disk, if available.
     * If loading fails for any reason, the app will continue with the default tab.
     */
    private void loadClipboardState() {
        File file = new File(CLIPBOARD_STATE_FILE);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object loadedTabs = ois.readObject();
            int loadedActiveIndex = ois.readInt();

            if (loadedTabs instanceof List<?>) {
                List<?> list = (List<?>) loadedTabs;
                if (!list.isEmpty() && list.get(0) instanceof ClipboardTab) {
                    tabs.clear();
                    for (Object obj : list) {
                        tabs.add((ClipboardTab) obj);
                    }
                    if (loadedActiveIndex >= 0 && loadedActiveIndex < tabs.size()) {
                        activeTabIndex = loadedActiveIndex;
                    } else {
                        activeTabIndex = 0;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
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
        time.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14)); // Updated font
        time.setForeground(new Color(110, 110, 125));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlPanel.setOpaque(false);

        JButton infoBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/info.svg", 16, 16));
        JButton deleteBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/close.svg", 16, 16));

        infoBtn.addActionListener(e -> showInfoPopup(item));
        deleteBtn.addActionListener(e -> {
            showConfirmationDialog("Delete Item", "Are you sure you want to delete this item?",
                    () -> deleteEntry(item, card));
        });

        // Move Button
        JButton moveBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/move.svg", 16, 16));
        moveBtn.setToolTipText("Move to another tab");
        moveBtn.addActionListener(e -> {
            JPopupMenu moveMenu = new JPopupMenu();
            for (int i = 0; i < tabs.size(); i++) {
                if (i == activeTabIndex)
                    continue;
                ClipboardTab targetTab = tabs.get(i);
                JMenuItem menuItem = new JMenuItem("Move to " + targetTab.name);
                menuItem.addActionListener(ev -> {
                    getCurrentTab().items.remove(item);
                    targetTab.items.add(0, item);
                    refreshUI();
                });
                moveMenu.add(menuItem);
            }
            if (moveMenu.getComponentCount() == 0) {
                JMenuItem empty = new JMenuItem("No other tabs");
                empty.setEnabled(false);
                moveMenu.add(empty);
            }
            moveMenu.show(moveBtn, 0, moveBtn.getHeight());
        });

        controlPanel.add(infoBtn);
        controlPanel.add(Box.createHorizontalStrut(8));
        controlPanel.add(moveBtn);
        controlPanel.add(Box.createHorizontalStrut(8));
        controlPanel.add(deleteBtn);

        headerPanel.add(time, BorderLayout.WEST);
        headerPanel.add(controlPanel, BorderLayout.EAST);
        card.add(headerPanel, BorderLayout.NORTH);

        // Content Area
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        JLabel preview = new JLabel();
        preview.setForeground(Color.WHITE);
        preview.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16)); // Updated font

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
        typeIndicator.setFont(getAppFont(FONT_FAMILY, Font.BOLD, 17)); // Updated font
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

    private void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.getContentPane().setBackground(new Color(18, 18, 20)); // Match App BG

        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel msgLabel = new JLabel(
                "<html><div style='width:250px'>" + message.replace("\n", "<br>") + "</div></html>");
        msgLabel.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 15));
        msgLabel.setForeground(Color.WHITE);
        panel.add(msgLabel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(new Color(45, 45, 52));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton confirmBtn = new JButton("Confirm");
        confirmBtn.setBackground(new Color(255, 60, 60)); // Red for destructive
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
        confirmBtn.addActionListener(e -> {
            dialog.dispose();
            onConfirm.run();
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(confirmBtn);

        panel.add(btnPanel, BorderLayout.SOUTH);
        dialog.add(panel);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
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
                getCurrentTab().items.remove(item); // Fix items.remove()
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
            key.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));

            JLabel val = new JLabel(detail[1]);
            val.setForeground(Color.WHITE);
            val.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));

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
            textArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16));
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

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            btnPanel.setOpaque(false);

            JButton scanBtn = new JButton("Scan Text (OCR)");
            scanBtn.setBackground(new Color(45, 45, 52));
            scanBtn.setForeground(Color.WHITE);
            scanBtn.setFocusPainted(false);
            scanBtn.setBorder(new EmptyBorder(8, 15, 8, 15));

            JButton searchBtn = new JButton("Visual Search");
            searchBtn.setBackground(new Color(45, 45, 52));
            searchBtn.setForeground(Color.WHITE);
            searchBtn.setFocusPainted(false);
            searchBtn.setBorder(new EmptyBorder(8, 15, 8, 15));

            btnPanel.add(scanBtn);
            btnPanel.add(searchBtn);

            JTextArea resultArea = new JTextArea();
            resultArea.setEditable(false);
            resultArea.setLineWrap(true);
            resultArea.setWrapStyleWord(true);
            resultArea.setBackground(new Color(28, 28, 32));
            resultArea.setForeground(Color.WHITE);
            resultArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
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

            searchBtn.addActionListener(e -> {
                searchBtn.setEnabled(false);
                searchBtn.setText("Searching...");
                new SwingWorker<String, Void>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        String engine = configManager.getSearchEngine();
                        return ocrService.getSearchUrl(item.getImage(), engine);
                    }

                    @Override
                    protected void done() {
                        try {
                            String url = get();
                            if (url != null) {
                                openBrowser(url);
                                searchBtn.setText("Search Complete");
                            } else {
                                searchBtn.setText("No URL Found");
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            searchBtn.setText("Search Failed");
                        } finally {
                            searchBtn.setEnabled(true);
                        }
                    }
                }.execute();
            });

            ocrPanel.add(btnPanel, BorderLayout.NORTH);
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

    private void showSettingsPopup() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.getContentPane().setBackground(new Color(18, 18, 20));

        JPanel mainPanel = new JPanel(new GridLayout(0, 1, 15, 15));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel browserLabel = new JLabel("Preferred Search Browser");
        browserLabel.setForeground(new Color(110, 110, 125));
        browserLabel.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));

        // Detect installed browsers
        List<String> detectedBrowsers = BrowserDetector.detectInstalledBrowsers();
        List<String> browserList = new ArrayList<>();
        browserList.add("System Default");
        browserList.addAll(detectedBrowsers);

        String[] browsers = browserList.toArray(new String[0]);
        JComboBox<String> browserCombo = new JComboBox<>(browsers);
        browserCombo.setSelectedItem(configManager.getBrowser());

        JLabel searchEngineLabel = new JLabel("Image Search Engine");
        searchEngineLabel.setForeground(new Color(110, 110, 125));
        searchEngineLabel.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));

        String[] searchEngines = { "Yandex", "Bing" };
        JComboBox<String> searchEngineCombo = new JComboBox<>(searchEngines);
        searchEngineCombo.setSelectedItem(configManager.getSearchEngine());

        JCheckBox incognitoCheck = new JCheckBox("Use Incognito/Private Mode");
        incognitoCheck.setOpaque(false);
        incognitoCheck.setForeground(Color.WHITE);
        incognitoCheck.setSelected(configManager.isIncognito());

        mainPanel.add(browserLabel);
        mainPanel.add(browserCombo);
        mainPanel.add(searchEngineLabel);
        mainPanel.add(searchEngineCombo);
        mainPanel.add(incognitoCheck);

        JButton saveBtn = new JButton("Save Preferences");
        saveBtn.setBackground(Color.WHITE);
        saveBtn.setForeground(new Color(18, 18, 20));
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> {
            configManager.setBrowser((String) browserCombo.getSelectedItem());
            configManager.setSearchEngine((String) searchEngineCombo.getSelectedItem());
            configManager.setIncognito(incognitoCheck.isSelected());
            configManager.save();
            dialog.dispose();
        });

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(saveBtn, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(350, dialog.getHeight());
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

    private void openBrowser(String url) {
        String browser = configManager.getBrowser();
        boolean incognito = configManager.isIncognito();

        // Use system default if selected and no incognito
        if ("System Default".equals(browser) && !incognito) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Use BrowserDetector for specific browsers
        try {
            String browserPath = BrowserDetector.getBrowserPath(browser);

            if (browserPath != null) {
                // Browser detected, use its path
                List<String> command = new ArrayList<>();
                command.add(browserPath);

                // Add incognito flag if enabled
                if (incognito) {
                    String incognitoFlag = BrowserDetector.getIncognitoFlag(browser);
                    if (incognitoFlag != null) {
                        command.add(incognitoFlag);
                    }
                }

                command.add(url);
                new ProcessBuilder(command).start();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback to system default
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
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
