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
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.awt.Desktop;
import java.awt.font.TextAttribute;
import java.time.format.DateTimeFormatter;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class App extends JFrame {
    private final JPanel contentPanel = new JPanel(null);
    private final JScrollPane scrollPane = new JScrollPane(contentPanel);
    private final ClipboardMonitor monitor;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");
    private final Map<ClipboardItem, AnimatedCard> cardMap = new HashMap<>();
    private Timer layoutTimer;

    // Custom Tabs Panel with Animation Support
    private class TabsPanel extends JPanel {
        private final List<JButton> tabButtons = new ArrayList<>();
        private JButton addButton;
        private final Rectangle highlightBounds = new Rectangle();
        private final Rectangle targetHighlightBounds = new Rectangle();
        private Timer animationTimer;
        
        private int draggedIndex = -1;
        private int dragOffsetX = 0;
        
        private boolean hasDragged = false;
        private int dragStartScreenX;
        
        public TabsPanel() {
            super(null);
            setOpaque(false);
            
            animationTimer = new Timer(16, e -> animate());
            animationTimer.start();
        }
        
        @Override
        public Dimension getPreferredSize() {
            int w = 0;
            for (JButton btn : tabButtons) {
                w += btn.getPreferredSize().width + 4;
            }
            if (addButton != null) {
                w += addButton.getPreferredSize().width + 4;
            }
            return new Dimension(w, 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (activeTabIndex >= 0 && activeTabIndex < tabButtons.size()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 45, 52));
                g2.fillRoundRect(highlightBounds.x, highlightBounds.y, highlightBounds.width, highlightBounds.height, 10, 10);
                g2.dispose();
            }
        }
        
        private void animate() {
            boolean changed = false;
            float speed = 0.2f;
            float threshold = 0.5f;

            int panelWidth = getWidth();
            if (panelWidth == 0) return; // Not laid out yet

            // Calculate dynamic tab width
            int addButtonWidth = (addButton != null) ? 30 + 4 : 0;
            int totalSpacing = (tabButtons.size()) * 4; // 4px spacing
            int availableWidth = panelWidth - addButtonWidth - totalSpacing;
            
            // Limit max width but ensure we fill if possible
            int targetTabWidth = 0;
            if (tabButtons.size() > 0) {
                 targetTabWidth = availableWidth / tabButtons.size();
                 // Optional: clamp max width if desired, e.g. 250
                 targetTabWidth = Math.min(targetTabWidth, 250);
                 // Ensure min width
                 targetTabWidth = Math.max(targetTabWidth, 80);
            }
            
            // Layout buttons
            int currentX = 0;
            for (int i = 0; i < tabButtons.size(); i++) {
                JButton btn = tabButtons.get(i);
                int targetX = currentX;
                int targetW = targetTabWidth;

                if (i == draggedIndex) {
                    // Skip animating dragged button X, handled by mouse drag
                    // But maybe animate width? 
                    // Let's animate width even if dragged
                    if (Math.abs(btn.getWidth() - targetW) > threshold) {
                         btn.setSize(btn.getWidth() + (int)((targetW - btn.getWidth()) * speed), btn.getHeight());
                         changed = true;
                    } else {
                         btn.setSize(targetW, btn.getHeight());
                    }
                } else {
                    int btnX = btn.getX();
                    int btnW = btn.getWidth();
                    
                    if (Math.abs(btnX - targetX) > threshold) {
                        btn.setLocation(btnX + (int)((targetX - btnX) * speed), btn.getY());
                        changed = true;
                    } else {
                        btn.setLocation(targetX, btn.getY());
                    }
                    
                    if (Math.abs(btnW - targetW) > threshold) {
                        btn.setSize(btnW + (int)((targetW - btnW) * speed), btn.getHeight());
                        changed = true;
                    } else {
                        btn.setSize(targetW, btn.getHeight());
                    }
                }
                
                // Update active highlight target
                if (i == activeTabIndex) {
                    targetHighlightBounds.setBounds(targetX, 0, targetW, btn.getHeight());
                }
                
                currentX += targetW + 4;
            }
            
            if (addButton != null) {
                int btnX = addButton.getX();
                if (Math.abs(btnX - currentX) > threshold) {
                    addButton.setLocation(btnX + (int)((currentX - btnX) * speed), addButton.getY());
                    changed = true;
                } else {
                    addButton.setLocation(currentX, addButton.getY());
                }
            }
            
            // Animate highlight
            if (Math.abs(highlightBounds.x - targetHighlightBounds.x) > threshold || 
                Math.abs(highlightBounds.width - targetHighlightBounds.width) > threshold) {
                highlightBounds.x += (targetHighlightBounds.x - highlightBounds.x) * speed;
                highlightBounds.width += (targetHighlightBounds.width - highlightBounds.width) * speed;
                highlightBounds.y = targetHighlightBounds.y;
                highlightBounds.height = targetHighlightBounds.height;
                changed = true;
            } else {
                highlightBounds.setBounds(targetHighlightBounds);
            }
            
            if (changed) repaint();
        }
        
        public void refresh(List<ClipboardTab> tabs, int activeIndex) {
            // Only rebuild if tab count/names changed or forced
            // For now, simpler to rebuild but we lose drag state if we are not careful.
            // But refresh() is called on adding/removing tabs, not during drag.
            
            removeAll();
            tabButtons.clear();
            
            int panelWidth = getWidth();
            int targetW = -1;
            if (panelWidth > 0 && !tabs.isEmpty()) {
                 int btnCount = tabs.size();
                 int addButtonWidth = (btnCount < 7) ? 34 : 0;
                 int availableWidth = panelWidth - addButtonWidth - (btnCount * 4);
                 targetW = availableWidth / btnCount;
                 targetW = Math.min(targetW, 250);
                 targetW = Math.max(targetW, 80);
            }
            
            int x = 0;
            for (int i = 0; i < tabs.size(); i++) {
                ClipboardTab tab = tabs.get(i);
                boolean isActive = (i == activeIndex);
                
                JButton btn = createTabButton(tab, i, isActive);
                // Calculate size immediately for layout
                if (targetW > 0) {
                    btn.setSize(targetW, btn.getPreferredSize().height);
                } else {
                    btn.setSize(btn.getPreferredSize());
                }
                btn.setLocation(x, 0);
                
                tabButtons.add(btn);
                add(btn);
                
                if (isActive) {
                    // If first load
                    if (highlightBounds.width == 0) {
                        highlightBounds.setBounds(x, 0, btn.getWidth(), btn.getHeight());
                    }
                    targetHighlightBounds.setBounds(x, 0, btn.getWidth(), btn.getHeight());
                }
                
                x += btn.getWidth() + 4;
            }
            
            // Add button logic
             if (tabs.size() < 7) {
                addButton = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/plus.svg", 16, 16));
                addButton.setToolTipText("New Tab");
                addButton.addActionListener(e -> {
                    tabs.add(new ClipboardTab("Tab " + (tabs.size() + 1)));
                    activeTabIndex = tabs.size() - 1; 
                    refreshTabsUI();
                    refreshUI();
                });
                addButton.setSize(30, 30); // fixed size
                addButton.setLocation(x, 0);
                add(addButton);
            } else {
                addButton = null;
            }
            
            revalidate();
            repaint();
        }
        
        private JButton createTabButton(ClipboardTab tab, int index, boolean isActive) {
            JButton tabBtn = new JButton(tab.name);
            tabBtn.setFont(getAppFont(FONT_FAMILY_TEXT, isActive ? Font.BOLD : Font.PLAIN, 14));
            tabBtn.setForeground(isActive ? Color.WHITE : new Color(110, 110, 125));
            tabBtn.setContentAreaFilled(false); 
            tabBtn.setFocusPainted(false);
            tabBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
            tabBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            // Click handler logic is now in mouseReleased to avoid conflict with drag
            /*
            tabBtn.addActionListener(e -> {
                int currentIdx = getButtonIndex(tabBtn);
                if (activeTabIndex != currentIdx && currentIdx != -1) {
                    activeTabIndex = currentIdx;
                    refreshTabsUI();
                    refreshUI();
                }
            });
            */
            
            // Context menu logic
            JPopupMenu tabMenu = new JPopupMenu();
            JMenuItem renameItem = new JMenuItem("Rename Tab");
            renameItem.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(App.this, "Enter new tab name:", tab.name);
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
                        if (activeTabIndex >= tabs.size()) activeTabIndex = 0;
                        refreshTabsUI();
                        refreshUI();
                    });
                });
                tabMenu.add(deleteItem);
            }
            tabBtn.setComponentPopupMenu(tabMenu);
            
            // Drag logic
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        draggedIndex = getButtonIndex(tabBtn);
                        if (draggedIndex != -1) {
                            dragOffsetX = e.getX();
                            dragStartScreenX = e.getXOnScreen();
                            hasDragged = false;
                            setComponentZOrder(tabBtn, 0); // Bring to front
                        }
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (!hasDragged && draggedIndex != -1) {
                            // Treat as click
                            int currentIdx = getButtonIndex(tabBtn);
                            if (activeTabIndex != currentIdx && currentIdx != -1) {
                                activeTabIndex = currentIdx;
                                refreshTabsUI();
                                refreshUI();
                            }
                        }
                        draggedIndex = -1;
                        repaint();
                    }
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && draggedIndex != -1) {
                        if (!hasDragged && Math.abs(e.getXOnScreen() - dragStartScreenX) > 5) {
                            hasDragged = true;
                        }
                        
                        if (hasDragged) {
                            int newX = tabBtn.getX() + e.getX() - dragOffsetX;
                            // Clamp
                            newX = Math.max(0, Math.min(newX, getWidth() - tabBtn.getWidth()));
                            tabBtn.setLocation(newX, 0);
                            
                            // Check for swap
                            int centerX = newX + tabBtn.getWidth() / 2;
                            
                            // Find button under center
                            for (int i = 0; i < tabButtons.size(); i++) {
                                if (i == draggedIndex) continue;
                                JButton other = tabButtons.get(i);
                                int otherCenter = other.getX() + other.getWidth() / 2;
                                
                                if (Math.abs(centerX - otherCenter) < other.getWidth() / 2) {
                                    // Swap
                                    Collections.swap(tabs, draggedIndex, i);
                                    // Swap buttons in list to keep sync
                                    Collections.swap(tabButtons, draggedIndex, i);
                                    
                                    // Update active index
                                    if (activeTabIndex == draggedIndex) activeTabIndex = i;
                                    else if (activeTabIndex == i) activeTabIndex = draggedIndex;
                                    
                                    draggedIndex = i; // Update dragged index
                                    break;
                                }
                            }
                        }
                    }
                }
            };
            tabBtn.addMouseListener(ma);
            tabBtn.addMouseMotionListener(ma);
            
            return tabBtn;
        }
        
        // Helper to find index dynamically
        private int getButtonIndex(JButton btn) {
            return tabButtons.indexOf(btn);
        }
    }

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
    private TabsPanel tabsPanel; // UI Container for tabs
    private JTextField searchField;
    private JComboBox<String> searchScopeCombo;
    private String searchQuery = "";

    private OcrService ocrService;
    private ConfigManager configManager = new ConfigManager();

    // Sleek Modern Fonts
    private static final String FONT_FAMILY = "Segoe UI Variable Display";
    private static final String FONT_FAMILY_TEXT = "Segoe UI Variable Text";
    private static final String FONT_FAMILY_TITLE = "Segoe UI Variable Display Semibold";

    // Helper to get font with fallback
    private Font getAppFont(String name, int style, float size) {
        return new Font(name, style, (int) size);
    }

    public App() {
        ocrService = new OcrService();
        setTitle("Clip-Pal");
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

        // Brand Section (Logo + Name)
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        brandPanel.setOpaque(false);

        // App Logo
        FlatSVGIcon logoIcon = new FlatSVGIcon("com/virtualclipboard/icons/logo_new.svg", 36, 48);
        JLabel logoLabel = new JLabel(logoIcon);
        brandPanel.add(logoLabel);

        // App Name
        JLabel titleLabel = new JLabel("Clip-Pal");
        titleLabel.setFont(getAppFont("Segoe UI Variable Display Semibold", Font.BOLD, 42));
        titleLabel.setForeground(Color.WHITE);
        brandPanel.add(titleLabel);

        headerPanel.add(brandPanel, BorderLayout.WEST);

        // Search Section
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(200, 35));
        searchField.setBackground(new Color(45, 45, 52));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 70), 1, true),
                new EmptyBorder(5, 10, 5, 10)));
        searchField.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));

        // Add a placeholder-like behavior or just a label
        FlatSVGIcon searchIcon = new FlatSVGIcon("com/virtualclipboard/icons/search.svg", 16, 16);
        searchIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> Color.WHITE));
        JLabel searchIconLabel = new JLabel(searchIcon);
        searchIconLabel.setBorder(new EmptyBorder(0, 0, 0, 10));

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchQuery = searchField.getText().toLowerCase();
                refreshUI();
            }
        });

        searchScopeCombo = new JComboBox<>(new String[] { "Current Tab", "All Tabs" });
        searchScopeCombo.setPreferredSize(new Dimension(120, 35));
        searchScopeCombo.addActionListener(e -> refreshUI());

        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.setOpaque(false);
        searchWrapper.add(searchIconLabel, BorderLayout.WEST);
        searchWrapper.add(searchField, BorderLayout.CENTER);
        searchWrapper.add(searchScopeCombo, BorderLayout.EAST);
        searchPanel.add(searchWrapper, BorderLayout.CENTER);

        // Tab Bar UI
        // Tab Bar UI
        tabsPanel = new TabsPanel(); 
        // tabsPanel.setOpaque(false); // Handled in TabsPanel constructor
        tabs.add(new ClipboardTab("Main")); // Default tab

        // Try to restore previous clipboard state (tabs and items)
        loadClipboardState();
        refreshTabsUI();
        refreshUI(); // Refresh UI to display loaded items

        JPanel centerHeader = new JPanel(new BorderLayout());
        centerHeader.setOpaque(false);
        centerHeader.add(tabsPanel, BorderLayout.SOUTH);
        centerHeader.setBorder(new EmptyBorder(0, 20, 0, 0));

        headerPanel.add(centerHeader, BorderLayout.CENTER);
        headerPanel.add(searchPanel, BorderLayout.SOUTH);

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
                    "Are you sure you want to clear all history?\nThis action cannot be undone.", () -> {
                        getCurrentTab().items.clear();
                        refreshUI();
                    });
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

        // Adaptive resize handling - Listen to viewport to ensure accurate width
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshUI();
            }
        });

        add(scrollPane, BorderLayout.CENTER);

        // Adaptive resize handling on frame removed in favor of viewport listener


        monitor = new ClipboardMonitor(this::addNewItem);

        // Save clipboard state when the window is closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveClipboardState();
            }
        });

        // Setup Drag and Drop
        setupDragAndDrop();
    }

    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = event.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File file : files) {
                            processDroppedFile(file);
                        }
                    } else if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        BufferedImage img = (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor);
                        if (img != null) {
                            addNewItem(new ClipboardItem(img));
                        }
                    } else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                        if (text != null && !text.isBlank()) {
                            addNewItem(new ClipboardItem(text));
                        }
                    }
                    event.dropComplete(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    event.dropComplete(false);
                }
            }
        });
    }

    private void processDroppedFile(File file) {
        if (file.isDirectory()) return;

        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".svg")) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                addNewItem(new ClipboardItem(content));
            } else if (name.endsWith(".gif")) {
                byte[] gifBytes = java.nio.file.Files.readAllBytes(file.toPath());
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(gifBytes));
                if (img != null) {
                    addNewItem(new ClipboardItem(gifBytes, img.getWidth(), img.getHeight()));
                }
            } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
                BufferedImage img = javax.imageio.ImageIO.read(file);
                if (img != null) {
                    addNewItem(new ClipboardItem(img));
                }
            } else {
                // Treat as text if not an image
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                addNewItem(new ClipboardItem(content));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addNewItem(ClipboardItem item) {
        SwingUtilities.invokeLater(() -> {
            ClipboardTab currentTab = getCurrentTab();
            // Check for duplicates
            if (!currentTab.items.isEmpty() && currentTab.items.get(0).equals(item)) {
                return;
            }
            currentTab.items.add(0, item);

            int max = configManager.getMaxHistory();
            while (currentTab.items.size() > max) {
                currentTab.items.remove(currentTab.items.size() - 1);
            }

            refreshUI();
            saveClipboardState();
        });
    }

    private ClipboardTab getCurrentTab() {
        if (tabs.isEmpty())
            tabs.add(new ClipboardTab("Main"));
        if (activeTabIndex >= tabs.size())
            activeTabIndex = 0;
        return tabs.get(activeTabIndex);
    }

    private void refreshTabsUI() {
        if (tabsPanel != null) {
            tabsPanel.refresh(tabs, activeTabIndex);
        }
    }

    private void refreshUI() {
        // Calculate grid columns and width
        int windowWidth = scrollPane.getViewport().getWidth();
        if (windowWidth <= 0) windowWidth = getWidth() - 20;

        int cols;
        if (windowWidth > 1300) cols = 4;
        else if (windowWidth > 900) cols = 3;
        else if (windowWidth > 600) cols = 2;
        else cols = 1;

        int baseCardWidth = (windowWidth - (cols * 20)) / cols;
        // Base height 200 scaled
        int baseCardHeight = (int) (200 * Math.max(0.8, (double) getHeight() / 1080.0));

        // Get items to show
        boolean searchAll = searchScopeCombo != null && "All Tabs".equals(searchScopeCombo.getSelectedItem());
        List<ClipboardTab> searchList = searchAll ? tabs : List.of(getCurrentTab());
        
        List<ClipboardItem> itemsToShow = new ArrayList<>();
        for (ClipboardTab tab : searchList) {
             for (ClipboardItem item : tab.items) {
                 if (!searchQuery.isEmpty()) {
                    if (item.getType() == ClipboardItem.Type.TEXT) {
                        if (item.getText() == null || !item.getText().toLowerCase().contains(searchQuery)) continue;
                    } else continue;
                }
                itemsToShow.add(item);
             }
        }

        // Identify items to remove (in cardMap but not in itemsToShow)
        List<ClipboardItem> toRemove = new ArrayList<>();
        for (ClipboardItem item : cardMap.keySet()) {
            if (!itemsToShow.contains(item)) {
                toRemove.add(item);
            }
        }

        for (ClipboardItem item : toRemove) {
            AnimatedCard card = cardMap.get(item);
            if (card != null) {
                // Animate out
                Timer t = new Timer(10, null);
                final float[] alpha = { 1.0f };
                t.addActionListener(e -> {
                    alpha[0] -= 0.1f;
                    if (alpha[0] <= 0.0f) {
                        t.stop();
                        contentPanel.remove(card);
                        contentPanel.repaint();
                    } else {
                        card.setAlpha(alpha[0]);
                        card.repaint();
                    }
                });
                t.start();
            }
            cardMap.remove(item); 
        }

        // Layout calculation
        boolean[][] occupied = new boolean[1000][cols];
        int currentMaxRow = 0;

        for (ClipboardItem item : itemsToShow) {
            int itemRows = item.getRows();
            int itemCols = Math.min(item.getCols(), cols);

            // Find slot
            int gridX = 0, gridY = 0;
            boolean found = false;
            for (int y = 0; y < 1000 && !found; y++) {
                for (int x = 0; x <= cols - itemCols; x++) {
                    boolean canFit = true;
                    for (int dy = 0; dy < itemRows; dy++) {
                        for (int dx = 0; dx < itemCols; dx++) {
                            if (occupied[y + dy][x + dx]) {
                                canFit = false;
                                break;
                            }
                        }
                        if (!canFit) break;
                    }
                    if (canFit) {
                        gridX = x;
                        gridY = y;
                        for (int dy = 0; dy < itemRows; dy++) {
                            for (int dx = 0; dx < itemCols; dx++) {
                                occupied[y + dy][x + dx] = true;
                            }
                        }
                        currentMaxRow = Math.max(currentMaxRow, y + itemRows);
                        found = true;
                        break;
                    }
                }
            }

            // Calculate target bounds
            // Assuming 20px gap, 10px margin
            int cardWidth = baseCardWidth * itemCols + (itemCols - 1) * 20;
            int cardHeight = baseCardHeight * itemRows + (itemRows - 1) * 20;
            int x = 10 + gridX * (baseCardWidth + 20);
            int y = 10 + gridY * (baseCardHeight + 20);

            AnimatedCard card = cardMap.get(item);
            if (card == null) {
                // New card
                card = createItemCard(item, windowWidth);
                contentPanel.add(card);
                cardMap.put(item, card);
                
                // Initial state
                card.setTargetBounds(x, y, cardWidth, cardHeight);
                // Start slightly lower for slide-up effect
                card.setBounds(x, y + 50, cardWidth, cardHeight);
                card.setAlpha(0.0f);
                
                // Fade in
                AnimatedCard finalCard = card;
                Timer t = new Timer(10, null);
                final float[] alpha = { 0.0f };
                t.addActionListener(e -> {
                    alpha[0] += 0.05f;
                    if (alpha[0] >= 1.0f) {
                        alpha[0] = 1.0f;
                        t.stop();
                    }
                    finalCard.setAlpha(alpha[0]);
                    finalCard.repaint();
                });
                t.start();
            } else {
                // Existing card, update target
                card.setTargetBounds(x, y, cardWidth, cardHeight);
            }
        }

        // Update preferred size
        int totalHeight = 10 + currentMaxRow * (baseCardHeight + 20);
        contentPanel.setPreferredSize(new Dimension(windowWidth, totalHeight));
        
        // Start layout animation loop
        if (layoutTimer == null || !layoutTimer.isRunning()) {
            layoutTimer = new Timer(16, e -> {
                boolean anyChanged = false;
                for (AnimatedCard c : cardMap.values()) {
                    if (c.animateStep()) anyChanged = true;
                }
                if (!anyChanged) {
                    ((Timer)e.getSource()).stop();
                }
                contentPanel.repaint(); // Repaint for smooth animation
            });
            layoutTimer.start();
        } else {
            // Ensure it continues if it was about to stop
            if (!layoutTimer.isRunning()) layoutTimer.start();
        }
        
        contentPanel.revalidate();
        contentPanel.repaint();
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

    private JComponent createScalableImageComponent(Image img, AnimatedCard card) {
        JPanel preview = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (img == null) return;

                int iw = img.getWidth(this);
                int ih = img.getHeight(this);
                if (iw <= 0 || ih <= 0) return;

                int cw = getWidth();
                int ch = getHeight();
                if (cw <= 0 || ch <= 0) return;

                double scale = Math.min((double) cw / iw, (double) ch / ih);
                int tw = (int) (iw * scale);
                int th = (int) (ih * scale);

                int x = (cw - tw) / 2;
                int y = (ch - th) / 2;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, x, y, tw, th, this);
                g2.dispose();
            }
        };
        preview.setOpaque(false);
        
        // Forward mouse events
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mousePressed(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseEntered(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseExited(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
        };
        preview.addMouseListener(ma);
        return preview;
    }

    private AnimatedCard createItemCard(ClipboardItem item, int windowWidth) {
        int windowHeight = getHeight();
        
        // Base dimensions relative to 1920x1080 reference
        double widthScale = (double) windowWidth / 1920.0;
        double heightScale = (double) windowHeight / 1080.0;
        
        int cols;
        if (windowWidth > 1300) cols = 4;
        else if (windowWidth > 900) cols = 3;
        else if (windowWidth > 600) cols = 2;
        else cols = 1;

        int baseCardWidth = (windowWidth - (cols * 20)) / cols; 
        // Increased base height by 25% (from 160 to 200) and scaled with window height
        int baseCardHeight = (int) (200 * Math.max(0.8, heightScale)); 
        
        int itemCols = Math.min(item.getCols(), cols);
        int itemRows = item.getRows();
        
        int cardWidth = baseCardWidth * itemCols + (itemCols - 1) * 20;
        int cardHeight = baseCardHeight * itemRows + (itemRows - 1) * 20;

        AnimatedCard card = new AnimatedCard(new BorderLayout(10, 5));
        card.setPreferredSize(new Dimension(cardWidth, cardHeight));
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Header Section (Time + Controls)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel time = new JLabel(item.getTimestamp().format(formatter));
        time.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
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
        controlPanel.add(Box.createHorizontalStrut(5));
        controlPanel.add(moveBtn);
        controlPanel.add(Box.createHorizontalStrut(5));
        controlPanel.add(deleteBtn);

        headerPanel.add(time, BorderLayout.WEST);
        headerPanel.add(controlPanel, BorderLayout.EAST);
        card.add(headerPanel, BorderLayout.NORTH);

        // Content Area
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);
        
        ClipboardItem.Type displayType = item.getType();

        if (item.getType() == ClipboardItem.Type.TEXT || item.getType() == ClipboardItem.Type.URL) {
            contentArea.add(createTextPreviewComponent(item, itemCols, itemRows, card), BorderLayout.CENTER);
        } else if (item.getType() == ClipboardItem.Type.SVG) {
            Component previewComponent;
            boolean isSvgRendered = false;
            try {
                byte[] svgBytes = item.getText().getBytes(StandardCharsets.UTF_8);
                // Create a temporary file for the SVG content to avoid InputStream issues with FlatSVGIcon
                File tempFile = File.createTempFile("clipboard_preview_", ".svg");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(svgBytes);
                }
                
                FlatSVGIcon svgIcon = new FlatSVGIcon(tempFile);
                
                // Validate SVG by forcing a render (catches parser errors early)
                BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = bi.createGraphics();
                try {
                    svgIcon.paintIcon(new JLabel(), g2d, 0, 0);
                } finally {
                    g2d.dispose();
                }

                // Scale SVG to fit while maintaining aspect ratio
                JLabel preview = new JLabel();
                preview.setHorizontalAlignment(SwingConstants.CENTER);
                int maxImgWidth = cardWidth - 40;
                int maxImgHeight = cardHeight - 80;

                float svgWidth = svgIcon.getIconWidth();
                float svgHeight = svgIcon.getIconHeight();
                float scale = Math.min(maxImgWidth / svgWidth, maxImgHeight / svgHeight);
                
                svgIcon = svgIcon.derive((int)(svgWidth * scale), (int)(svgHeight * scale));
                preview.setIcon(svgIcon);
                
                // Add mouse listener for consistency
                preview.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
                    @Override public void mousePressed(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
                    @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
                    @Override public void mouseEntered(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
                    @Override public void mouseExited(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
                });
                
                previewComponent = preview;
                isSvgRendered = true;
            } catch (Throwable e) {
                // Fallback to text preview on any error
                previewComponent = createTextPreviewComponent(item, itemCols, itemRows, card);
                isSvgRendered = false;
            }
            
            if (!isSvgRendered) {
                displayType = ClipboardItem.Type.TEXT;
            }
            contentArea.add(previewComponent, BorderLayout.CENTER);
        } else if (item.getType() == ClipboardItem.Type.GIF) {
            final ImageIcon icon = new ImageIcon(item.getGifData());
            contentArea.add(createScalableImageComponent(icon.getImage(), card), BorderLayout.CENTER);
        } else {
            contentArea.add(createScalableImageComponent(item.getImage(), card), BorderLayout.CENTER);
        }
        card.add(contentArea, BorderLayout.CENTER);

        // Type Indicator (Bottom Right)
        String typeStr = "T";
        if (displayType == ClipboardItem.Type.IMAGE) typeStr = "I";
        else if (displayType == ClipboardItem.Type.URL) typeStr = "U";
        else if (displayType == ClipboardItem.Type.SVG) typeStr = "S";
        else if (displayType == ClipboardItem.Type.GIF) typeStr = "G";
        
        JLabel typeIndicator = new JLabel(typeStr);
        typeIndicator.setFont(getAppFont(FONT_FAMILY, Font.BOLD, configManager.getFontSize() + 3));
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
                if (item.getType() == ClipboardItem.Type.URL && e.isShiftDown()) {
                    openBrowser(item.getText());
                } else {
                    copyToSystemClipboard(item);
                    animateCopyFeedback(card, time, item);
                }
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
                card.setFeedbackProgress(ratio);
            } else {
                pulseTimer.stop();
                card.setFeedbackProgress(1.0f);
                Timer resetTimer = new Timer(1200, evt -> {
                    timeLabel.setText(originalTimestamp);
                    timeLabel.setForeground(new Color(110, 110, 125));
                    card.setFeedbackProgress(-1.0f);
                });
                resetTimer.setRepeats(false);
                card.setResetTimer(resetTimer);
                resetTimer.start();
            }
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
        monitor.resetIfCurrent(item);
        getCurrentTab().items.remove(item);
        refreshUI();
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

        if (item.getType() == ClipboardItem.Type.TEXT || item.getType() == ClipboardItem.Type.URL) {
            details.add(new String[] { "Characters", String.valueOf(item.getCharacterCount()) });
            details.add(new String[] { "Words", String.valueOf(item.getWordCount()) });
            details.add(new String[] { "Lines", String.valueOf(item.getLineCount()) });
            
            if (item.getType() == ClipboardItem.Type.URL) {
                details.add(new String[] { "Domain", item.getUrlDomain() });
                details.add(new String[] { "Protocol", item.getUrlProtocol() });
            }
        } else if (item.getType() == ClipboardItem.Type.SVG) {
            // For SVG, try to show dimensions if renderable, else show text stats
            boolean isRenderable = false;
            try {
                // Quick check if renderable
                File tempFile = File.createTempFile("clipboard_info_", ".svg");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(item.getText().getBytes(StandardCharsets.UTF_8));
                }
                FlatSVGIcon svgIcon = new FlatSVGIcon(tempFile);
                if (svgIcon.getIconWidth() > 0) {
                     details.add(new String[] { "Dimensions", svgIcon.getIconWidth() + " x " + svgIcon.getIconHeight() });
                     isRenderable = true;
                }
            } catch (Throwable e) {
                isRenderable = false;
            }
            
            if (!isRenderable) {
                details.add(new String[] { "Status", "Raw Code (Render Failed)" });
            }
            // Always show code stats for SVG as it is code-based
            details.add(new String[] { "Characters", String.valueOf(item.getCharacterCount()) });
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
        boolean showAsText = item.getType() == ClipboardItem.Type.TEXT || item.getType() == ClipboardItem.Type.URL;
        FlatSVGIcon svgIcon = null;

        if (item.getType() == ClipboardItem.Type.SVG) {
            try {
                byte[] svgBytes = item.getText().getBytes(StandardCharsets.UTF_8);
                File tempFile = File.createTempFile("clipboard_popup_", ".svg");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(svgBytes);
                }
                svgIcon = new FlatSVGIcon(tempFile);
                
                // Validate
                BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = bi.createGraphics();
                try {
                    svgIcon.paintIcon(new JLabel(), g2d, 0, 0);
                } finally {
                    g2d.dispose();
                }
            } catch (Throwable e) {
                showAsText = true;
            }
        }

        if (showAsText) {
            JTextArea textArea = new JTextArea(item.getText());
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(new Color(28, 28, 32));
            
            if (item.getType() == ClipboardItem.Type.URL) {
                textArea.setForeground(getThemeColor("accent"));
                Map<TextAttribute, Object> attributes = new HashMap<>();
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                textArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16).deriveFont(attributes));
            } else {
                textArea.setForeground(Color.WHITE);
                textArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16));
            }
            
            textArea.setCaretColor(Color.WHITE);
            textArea.setBorder(new EmptyBorder(15, 15, 15, 15));

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(500, 300));
            scroll.setBorder(new LineBorder(new Color(45, 45, 52)));
            mainPanel.add(scroll, BorderLayout.CENTER);
        } else {
            JPanel imageContainer = new JPanel(new BorderLayout(10, 10));
            imageContainer.setOpaque(false);

            JLabel imgLabel = new JLabel();
            if (item.getType() == ClipboardItem.Type.SVG) {
                // Scale SVG to fit
                int maxWidth = 500;
                int maxHeight = 400;
                float iw = svgIcon.getIconWidth();
                float ih = svgIcon.getIconHeight();
                float scale = 1.0f;
                
                if (iw > maxWidth || ih > maxHeight) {
                    scale = Math.min((float)maxWidth / iw, (float)maxHeight / ih);
                }
                
                if (scale != 1.0f) {
                    svgIcon = svgIcon.derive((int)(iw * scale), (int)(ih * scale));
                }
                imgLabel.setIcon(svgIcon);
            } else if (item.getType() == ClipboardItem.Type.GIF) {
                int imgWidth = item.getWidth();
                int imgHeight = item.getHeight();
                ImageIcon icon = new ImageIcon(item.getGifData());
                
                if (imgWidth > 500) {
                    double scale = 500.0 / imgWidth;
                    int targetWidth = 500;
                    int targetHeight = (int) (imgHeight * scale);
                    imgLabel.setIcon(new Icon() {
                        @Override
                        public void paintIcon(Component c, Graphics g, int x, int y) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.drawImage(icon.getImage(), x, y, targetWidth, targetHeight, c);
                            g2.dispose();
                        }
                        @Override
                        public int getIconWidth() { return targetWidth; }
                        @Override
                        public int getIconHeight() { return targetHeight; }
                    });
                    imgLabel.setPreferredSize(new Dimension(targetWidth, targetHeight));
                } else {
                    imgLabel.setIcon(icon);
                    imgLabel.setPreferredSize(new Dimension(imgWidth, imgHeight));
                }
            } else if (item.getType() == ClipboardItem.Type.IMAGE) {
                imgLabel.setIcon(new ImageIcon(item.getImage().getScaledInstance(
                        item.getWidth() > 500 ? 500 : -1,
                        -1,
                        Image.SCALE_SMOOTH)));
            }
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
                        return ocrService.extractText(item.getAsImage());
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
                        return ocrService.getSearchUrl(item.getAsImage(), engine);
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
        Color bgMain = getThemeColor("bgMain");
        Color accent = getThemeColor("accent");
        Color textPrimary = getThemeColor("textPrimary");
        Color textSecondary = getThemeColor("textSecondary");

        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(bgMain);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        // Header
        JLabel headerLabel = new JLabel("Preferences");
        headerLabel.setForeground(textPrimary);
        headerLabel.setFont(getAppFont(FONT_FAMILY_TITLE, Font.BOLD, 24));
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(headerLabel);
        contentPanel.add(Box.createVerticalStrut(25));

        // Group 1: General Appearance
        contentPanel.add(createSectionHeader("Appearance", accent));
        
        contentPanel.add(createSettingLabel("Color Palette", textSecondary));
        String[] themes = { "Dark", "Deep Ocean", "Forest", "Sunset" };
        JComboBox<String> themeCombo = createStyledComboBox(themes, configManager.getTheme());
        contentPanel.add(themeCombo);
        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createSettingLabel("Font Size", textSecondary));
        Integer[] fontSizes = { 12, 14, 16, 18, 20 };
        JComboBox<Integer> fontCombo = createStyledComboBox(fontSizes, configManager.getFontSize());
        contentPanel.add(fontCombo);
        contentPanel.add(Box.createVerticalStrut(30));

        // Group 2: Search & Behavior
        contentPanel.add(createSectionHeader("Search & Tools", accent));
        
        contentPanel.add(createSettingLabel("Preferred Browser", textSecondary));
        List<String> detectedBrowsers = BrowserDetector.detectInstalledBrowsers();
        List<String> browserList = new ArrayList<>();
        browserList.add("System Default");
        browserList.addAll(detectedBrowsers);
        JComboBox<String> browserCombo = createStyledComboBox(browserList.toArray(new String[0]), configManager.getBrowser());
        contentPanel.add(browserCombo);
        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createSettingLabel("Image Search Engine", textSecondary));
        String[] searchEngines = { "Google", "Yandex", "Bing" };
        JComboBox<String> searchEngineCombo = createStyledComboBox(searchEngines, configManager.getSearchEngine());
        contentPanel.add(searchEngineCombo);
        contentPanel.add(Box.createVerticalStrut(30));

        // Group 3: History
        contentPanel.add(createSectionHeader("History", accent));
        
        contentPanel.add(createSettingLabel("Max History Items", textSecondary));
        Integer[] historyLimits = { 25, 50, 100, 200, 500 };
        JComboBox<Integer> historyCombo = createStyledComboBox(historyLimits, configManager.getMaxHistory());
        contentPanel.add(historyCombo);
        contentPanel.add(Box.createVerticalStrut(25));

        // Switches
        JCheckBox incognitoCheck = createSettingCheckbox("Use Incognito Mode", configManager.isIncognito(), textPrimary);
        JCheckBox autoStartCheck = createSettingCheckbox("Start with Windows", configManager.isAutoStart(), textPrimary);
        contentPanel.add(incognitoCheck);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(autoStartCheck);
        contentPanel.add(Box.createVerticalStrut(40));

        // Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn = new JButton("Save & Apply");
        saveBtn.setBackground(accent);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        saveBtn.setBorder(new EmptyBorder(12, 25, 12, 25));
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        saveBtn.addActionListener(e -> {
            configManager.setBrowser((String) browserCombo.getSelectedItem());
            configManager.setSearchEngine((String) searchEngineCombo.getSelectedItem());
            configManager.setTheme((String) themeCombo.getSelectedItem());
            configManager.setFontSize((Integer) fontCombo.getSelectedItem());
            configManager.setMaxHistory((Integer) historyCombo.getSelectedItem());
            configManager.setIncognito(incognitoCheck.isSelected());
            configManager.setAutoStart(autoStartCheck.isSelected());
            configManager.save();
            applySettings();
            dialog.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(new Color(45, 45, 48));
        cancelBtn.setForeground(new Color(200, 200, 210));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        cancelBtn.setBorder(new EmptyBorder(12, 25, 12, 25));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(cancelBtn);
        contentPanel.add(buttonPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setSize(450, 780);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JLabel createSectionHeader(String text, Color color) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setForeground(color);
        label.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 10, 0));
        return label;
    }

    private <T> JComboBox<T> createStyledComboBox(T[] items, T selectedValue) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setSelectedItem(selectedValue);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
        return combo;
    }

    private JLabel createSettingLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    private JCheckBox createSettingCheckbox(String text, boolean selected, Color color) {
        JCheckBox cb = new JCheckBox(text);
        cb.setOpaque(false);
        cb.setForeground(color);
        cb.setSelected(selected);
        cb.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return cb;
    }

    private Color getThemeColor(String key) {
        String theme = configManager.getTheme();
        switch (theme) {
            case "Deep Ocean":
                if ("bgMain".equals(key)) return new Color(10, 25, 40);
                if ("accent".equals(key)) return new Color(60, 160, 255);
                if ("textPrimary".equals(key)) return Color.WHITE;
                if ("textSecondary".equals(key)) return new Color(150, 180, 210);
                break;
            case "Forest":
                if ("bgMain".equals(key)) return new Color(15, 30, 20);
                if ("accent".equals(key)) return new Color(80, 200, 120);
                if ("textPrimary".equals(key)) return new Color(230, 245, 230);
                if ("textSecondary".equals(key)) return new Color(140, 170, 145);
                break;
            case "Sunset":
                if ("bgMain".equals(key)) return new Color(40, 20, 30);
                if ("accent".equals(key)) return new Color(255, 100, 100);
                if ("textPrimary".equals(key)) return new Color(255, 240, 240);
                if ("textSecondary".equals(key)) return new Color(210, 160, 170);
                break;
            default: // Dark
                if ("bgMain".equals(key)) return new Color(18, 18, 20);
                if ("accent".equals(key)) return new Color(60, 120, 255);
                if ("textPrimary".equals(key)) return Color.WHITE;
                if ("textSecondary".equals(key)) return new Color(180, 180, 190);
                break;
        }
        return Color.WHITE;
    }

    private void applySettings() {
        Color bgMain = getThemeColor("bgMain");
        getContentPane().setBackground(bgMain);
        refreshUI();
    }

    private Component createTextPreviewComponent(ClipboardItem item, int itemCols, int itemRows, JPanel card) {
        JTextArea preview = new JTextArea();
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setEditable(false);
        preview.setOpaque(false);
        preview.setFocusable(false);
        preview.setMargin(new Insets(0, 0, 0, 0));
        preview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        if (item.getType() == ClipboardItem.Type.URL) {
            preview.setForeground(getThemeColor("accent"));
            Map<TextAttribute, Object> attributes = new HashMap<>();
            attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            preview.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, configManager.getFontSize() + 2).deriveFont(attributes));
        } else {
            preview.setForeground(Color.WHITE);
            preview.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, configManager.getFontSize() + 2));
        }

        String text = item.getText().trim();
        int maxChars = (itemCols * itemRows > 1) ? 600 : 250;
        if (text.length() > maxChars)
            text = text.substring(0, maxChars - 3) + "...";
        preview.setText(text.replace("\n", " "));
        
        // Forward mouse events to the card
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mousePressed(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseEntered(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
            @Override public void mouseExited(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(preview, e, card)); }
        };
        preview.addMouseListener(ma);
        return preview;
    }

    // Custom Component for Opacity Support
    private class AnimatedCard extends JPanel {
        private float alpha = 1.0f;
        private boolean hovered = false;
        private float feedbackProgress = -1.0f;
        private Timer pulseTimer;
        private Timer resetTimer;
        
        private Rectangle targetBounds;
        private float currentX, currentY, currentW, currentH;

        public AnimatedCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }
        
        public void setTargetBounds(int x, int y, int width, int height) {
             if (targetBounds == null) {
                 // First time initialization
                 currentX = x;
                 currentY = y;
                 currentW = width;
                 currentH = height;
                 setBounds(x, y, width, height);
             }
             this.targetBounds = new Rectangle(x, y, width, height);
        }

        public boolean animateStep() {
             if (targetBounds == null) return false;
             
             boolean changed = false;
             float speed = 0.2f; // Animation speed
             float threshold = 0.5f;

             if (Math.abs(targetBounds.x - currentX) > threshold) {
                 currentX += (targetBounds.x - currentX) * speed;
                 changed = true;
             } else currentX = targetBounds.x;

             if (Math.abs(targetBounds.y - currentY) > threshold) {
                 currentY += (targetBounds.y - currentY) * speed;
                 changed = true;
             } else currentY = targetBounds.y;
             
             if (Math.abs(targetBounds.width - currentW) > threshold) {
                 currentW += (targetBounds.width - currentW) * speed;
                 changed = true;
             } else currentW = targetBounds.width;
             
             if (Math.abs(targetBounds.height - currentH) > threshold) {
                 currentH += (targetBounds.height - currentH) * speed;
                 changed = true;
             } else currentH = targetBounds.height;
             
             if (changed) {
                super.setBounds((int)currentX, (int)currentY, (int)currentW, (int)currentH);
                revalidate();
                doLayout(); // Force immediate layout for children alignment
            } else {
                super.setBounds(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
                revalidate();
                doLayout();
            }
            return changed;
        }

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        public void setHovered(boolean hovered) {
            this.hovered = hovered;
            repaint();
        }

        public void setFeedbackProgress(float progress) {
            this.feedbackProgress = progress;
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

            String theme = configManager.getTheme();
            Color body;
            Color topLeft;
            Color bottomRight;
            Color accent = Color.WHITE;

            switch (theme) {
                case "Deep Ocean":
                    body = new Color(20, 45, 70);
                    topLeft = new Color(30, 65, 95);
                    bottomRight = new Color(15, 35, 55);
                    break;
                case "Forest":
                    body = new Color(25, 50, 35);
                    topLeft = new Color(35, 70, 50);
                    bottomRight = new Color(20, 40, 30);
                    break;
                case "Sunset":
                    body = new Color(70, 35, 45);
                    topLeft = new Color(95, 50, 65);
                    bottomRight = new Color(55, 25, 35);
                    break;
                default: // Dark
                    body = new Color(0x222226);
                    topLeft = new Color(0x29292D);
                    bottomRight = new Color(0x1C1C20);
                    break;
            }

            // Main body
            g2.setColor(body);
            g2.fillRect(0, 0, w, h);

            if (feedbackProgress >= 0) {
                g2.setColor(Color.WHITE);
                int thickness = (int) (2 + (5 * (1 - feedbackProgress)));
                g2.setStroke(new BasicStroke(thickness));
                int offset = thickness / 2;
                g2.drawRect(offset, offset, w - thickness, h - thickness);
            } else if (hovered) {
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
        if (item.getType() == ClipboardItem.Type.TEXT || item.getType() == ClipboardItem.Type.URL) {
            String text = item.getText();
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            monitor.updateLastContent(text);
        } else if (item.getType() == ClipboardItem.Type.SVG) {
            // For SVG, try to render it as an image for the clipboard
            BufferedImage image = null;
            try {
                byte[] svgBytes = item.getText().getBytes(StandardCharsets.UTF_8);
                File tempFile = File.createTempFile("clipboard_copy_", ".svg");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(svgBytes);
                }
                
                FlatSVGIcon svgIcon = new FlatSVGIcon(tempFile);
                int w = svgIcon.getIconWidth();
                int h = svgIcon.getIconHeight();
                
                // If dimensions are too small or invalid, pick a reasonable default
                if (w <= 0 || h <= 0) { w = 500; h = 500; }
                
                // Render to BufferedImage
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();
                svgIcon.paintIcon(new JLabel(), g2, 0, 0);
                g2.dispose();
                
            } catch (Throwable e) {
                // If rendering fails, fallback to text
                image = null;
            }

            if (image != null) {
                final BufferedImage finalImage = image;
                Transferable imageTransferable = new Transferable() {
                    @Override
                    public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[] { DataFlavor.imageFlavor, DataFlavor.stringFlavor };
                    }

                    @Override
                    public boolean isDataFlavorSupported(DataFlavor flavor) {
                        return DataFlavor.imageFlavor.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
                    }

                    @Override
                    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                        if (DataFlavor.imageFlavor.equals(flavor)) {
                            return finalImage;
                        } else if (DataFlavor.stringFlavor.equals(flavor)) {
                            return item.getText();
                        }
                        throw new UnsupportedFlavorException(flavor);
                    }
                };
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(imageTransferable, null);
                monitor.updateLastContent(item.getText());
            } else {
                // Fallback to text copy
                String text = item.getText();
                StringSelection selection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                monitor.updateLastContent(text);
            }

        } else if (item.getType() == ClipboardItem.Type.IMAGE || item.getType() == ClipboardItem.Type.GIF) {
            BufferedImage image = item.getAsImage();
            if (image == null) return;
            
            final BufferedImage finalImage = image;
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
                        return finalImage;
                    throw new UnsupportedFlavorException(flavor);
                }
            };
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(imageTransferable, null);
            monitor.updateLastContent(finalImage);
        }
    }

    private void openBrowser(String url) {
        if (url == null || url.isBlank()) return;
        
        // Ensure protocol
        String finalUrl = url.toLowerCase().startsWith("http") ? url : "http://" + url;
        
        String browser = configManager.getBrowser();
        boolean incognito = configManager.isIncognito();

        // Use system default if selected and no incognito
        if ("System Default".equals(browser) && !incognito) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(finalUrl));
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

                command.add(finalUrl);
                new ProcessBuilder(command).start();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback to system default
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(finalUrl));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void start() {
        // Prevent loading the current system clipboard as a new item on startup
        try {
            java.awt.datatransfer.Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = sysClip.getContents(null);
            if (contents != null) {
                if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    try {
                        String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                        monitor.updateLastContent(text);
                    } catch (Exception ignored) {
                    }
                } else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    try {
                        Image img = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                        if (img instanceof BufferedImage) {
                            monitor.updateLastContent((BufferedImage) img);
                        } else if (img != null) {
                            // Convert Image to BufferedImage if necessary
                            BufferedImage bImg = new BufferedImage(img.getWidth(null), img.getHeight(null),
                                    BufferedImage.TYPE_INT_ARGB);
                            Graphics g = bImg.getGraphics();
                            g.drawImage(img, 0, 0, null);
                            g.dispose();
                            monitor.updateLastContent(bImg);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            // Ignore startup clipboard errors
        }

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