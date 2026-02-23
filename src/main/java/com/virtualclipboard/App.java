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
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");
    private final Map<ClipboardItem, AnimatedCard> cardMap = new HashMap<>();
    private Timer layoutTimer;
    private final Map<String, Font> fontCache = new HashMap<>();

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
        
        private int currentTabWidth = 100; // Store for drag calculation
        
        private final JComponent highlightComponent;
        private boolean isHighlightRaised = false;

        public TabsPanel() {
            super(null);
            setOpaque(false);
            
            highlightComponent = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getThemeColor("inputBackground"));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    
                    g2.dispose();
                }
            };
            add(highlightComponent);
            
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
            return new Dimension(w, 54);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
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
            
            this.currentTabWidth = targetTabWidth;
            
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
                    int btnY = btn.getY();
                    int btnW = btn.getWidth();
                    int targetY = Math.max(0, (getHeight() - btn.getHeight()) / 2);
                    
                    if (Math.abs(btnX - targetX) > threshold || Math.abs(btnY - targetY) > threshold) {
                        int newX = (Math.abs(btnX - targetX) > threshold) ? btnX + (int)((targetX - btnX) * speed) : targetX;
                        int newY = (Math.abs(btnY - targetY) > threshold) ? btnY + (int)((targetY - btnY) * speed) : targetY;
                        btn.setLocation(newX, newY);
                        changed = true;
                    } else {
                        btn.setLocation(targetX, targetY);
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
                    if (i == draggedIndex) {
                        targetHighlightBounds.setBounds(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
                        highlightBounds.setBounds(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
                    } else {
                        targetHighlightBounds.setBounds(targetX, btn.getY(), targetW, btn.getHeight());
                    }
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
            
            highlightComponent.setBounds(highlightBounds);
            highlightComponent.repaint(); // Ensure it repaints
            
            // Z-Order Management
            if (draggedIndex != -1 && draggedIndex < tabButtons.size()) {
                JButton draggedBtn = tabButtons.get(draggedIndex);
                
                if (draggedIndex == activeTabIndex) {
                    // Dragging ACTIVE tab: It stays on top
                    // 1. Dragged Active Tab (Text) -> Z=0
                    // 2. Highlight (Background) -> Z=1
                    if (getComponentZOrder(draggedBtn) != 0) {
                        setComponentZOrder(draggedBtn, 0);
                    }
                    if (getComponentZOrder(highlightComponent) != 1) {
                        setComponentZOrder(highlightComponent, 1);
                    }
                } else {
                    // Dragging INACTIVE tab: Render BEHIND active tab
                    // 1. Active Tab (Text) -> Z=0
                    // 2. Highlight (Background) -> Z=1
                    // 3. Dragged Inactive Tab -> Z=2
                    
                    if (activeTabIndex >= 0 && activeTabIndex < tabButtons.size()) {
                        JButton activeBtn = tabButtons.get(activeTabIndex);
                        if (getComponentZOrder(activeBtn) != 0) {
                            setComponentZOrder(activeBtn, 0);
                        }
                        if (getComponentZOrder(highlightComponent) != 1) {
                            setComponentZOrder(highlightComponent, 1);
                        }
                        if (getComponentZOrder(draggedBtn) != 2) {
                            setComponentZOrder(draggedBtn, 2);
                        }
                    } else {
                        // Fallback (no active tab?)
                        if (getComponentZOrder(draggedBtn) != 0) {
                            setComponentZOrder(draggedBtn, 0);
                        }
                    }
                }
                isHighlightRaised = true;
            } else {
                // Not dragging: Highlight at bottom (behind text of all tabs)
                int lastIndex = getComponentCount() - 1;
                if (getComponentZOrder(highlightComponent) != lastIndex) {
                    setComponentZOrder(highlightComponent, lastIndex);
                }
                isHighlightRaised = false;
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
                 int addButtonWidth = (btnCount < 10) ? 34 : 0;
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
                
                int y = Math.max(0, (54 - btn.getHeight()) / 2);
                btn.setLocation(x, y);
                
                tabButtons.add(btn);
                add(btn);
                
                if (isActive) {
                    // If first load
                    if (highlightBounds.width == 0) {
                        highlightBounds.setBounds(x, y, btn.getWidth(), btn.getHeight());
                    }
                    targetHighlightBounds.setBounds(x, y, btn.getWidth(), btn.getHeight());
                }
                
                x += btn.getWidth() + 4;
            }
            
            // Add button logic
             if (tabs.size() < 10) {
                addButton = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/plus.svg", 16, 16));
                addButton.setToolTipText("New Tab");
                addButton.addActionListener(e -> {
                    tabs.add(new ClipboardTab("Tab " + (tabs.size() + 1)));
                    queueTabSwitch(tabs.size() - 1);
                });
                addButton.setSize(30, 30); // fixed size
                addButton.setLocation(x, (54 - 30) / 2);
                add(addButton);
            } else {
                addButton = null;
            }
            
            // Add highlight component last so it defaults to bottom
            add(highlightComponent);
            
            revalidate();
            repaint();
        }
        
        private JButton createTabButton(ClipboardTab tab, int index, boolean isActive) {
            JButton tabBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    if (!isActive) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // Subtle dark background for inactive tabs
                        Color inactiveBg = new Color(0, 0, 0, 30);
                        if ("Twilight".equals(configManager.getTheme())) {
                             inactiveBg = new Color(0, 0, 0, 40); // Slightly darker for Twilight
                        }
                        g2.setColor(inactiveBg);
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            
            if (tab.iconValue != null) {
                if (tab.isEmoji) {
                    tabBtn.setText(tab.iconValue + " " + tab.name);
                } else {
                    FlatSVGIcon icon = new FlatSVGIcon(tab.iconValue, 16, 16);
                    if (isActive) {
                        if (!configManager.isHighContrast() && "Twilight".equals(configManager.getTheme())) {
                            icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("bgMain")));
                        } else {
                            icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("inputText")));
                        }
                    } else {
                        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
                    }
                    tabBtn.setIcon(icon);
                    tabBtn.setText(tab.name);
                    tabBtn.setIconTextGap(8);
                }
            } else {
                tabBtn.setText(tab.name);
            }

            if (isActive) {
                tabBtn.setFont(getAppFont("Roboto Black", Font.PLAIN, 14));
            } else {
                tabBtn.setFont(getAppFont("Roboto", Font.PLAIN, 14));
            }
            if (isActive && !configManager.isHighContrast() && "Twilight".equals(configManager.getTheme())) {
                tabBtn.setForeground(getThemeColor("bgMain"));
            } else {
                tabBtn.setForeground(isActive ? getThemeColor("inputText") : getThemeColor("textSecondary"));
            }
            tabBtn.setContentAreaFilled(false); 
            tabBtn.setFocusPainted(false);
            tabBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
            tabBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tabBtn.setHorizontalAlignment(SwingConstants.CENTER);
            
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
            // Explicit border to ensure dynamic theme update
            tabMenu.setBorder(new EmptyBorder(0,0,0,0));
            
            JMenuItem changeIconItem = new JMenuItem("Change Icon...");
            changeIconItem.setBorder(new EmptyBorder(5, 10, 5, 10));
            changeIconItem.addActionListener(e -> showIconSelector(tab));
            tabMenu.add(changeIconItem);
            
            // Sort Options
            JMenu sortMenu = new JMenu("Sort by...");
            sortMenu.setBorder(new EmptyBorder(5, 10, 5, 10)); // Fix alignment
            sortMenu.getPopupMenu().setBorder(new EmptyBorder(0,0,0,0)); // Ensure sub-menu has no outline
            
            JMenuItem sortDateNewOld = new JMenuItem("Date (Newest First)");
            sortDateNewOld.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortDateNewOld.addActionListener(e -> {
                sortItems(tab, (a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            });
            sortMenu.add(sortDateNewOld);

            JMenuItem sortDateOldNew = new JMenuItem("Date (Oldest First)");
            sortDateOldNew.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortDateOldNew.addActionListener(e -> {
                sortItems(tab, (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
            });
            sortMenu.add(sortDateOldNew);

            sortMenu.addSeparator();

            JMenuItem sortTypeAZ = new JMenuItem("Type (A-Z)");
            sortTypeAZ.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortTypeAZ.addActionListener(e -> {
                sortItems(tab, (a, b) -> a.getType().toString().compareTo(b.getType().toString()));
            });
            sortMenu.add(sortTypeAZ);
            
            JMenuItem sortTypeZA = new JMenuItem("Type (Z-A)");
            sortTypeZA.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortTypeZA.addActionListener(e -> {
                sortItems(tab, (a, b) -> b.getType().toString().compareTo(a.getType().toString()));
            });
            sortMenu.add(sortTypeZA);

            sortMenu.addSeparator();

            JMenuItem sortSizeSmallLarge = new JMenuItem("Size (Smallest First)");
            sortSizeSmallLarge.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortSizeSmallLarge.addActionListener(e -> {
                sortItems(tab, (a, b) -> Long.compare(a.getSizeInBytes(), b.getSizeInBytes()));
            });
            sortMenu.add(sortSizeSmallLarge);

            JMenuItem sortSizeLargeSmall = new JMenuItem("Size (Largest First)");
            sortSizeLargeSmall.setBorder(new EmptyBorder(5, 10, 5, 10));
            sortSizeLargeSmall.addActionListener(e -> {
                sortItems(tab, (a, b) -> Long.compare(b.getSizeInBytes(), a.getSizeInBytes()));
            });
            sortMenu.add(sortSizeLargeSmall);

            tabMenu.add(sortMenu);
            tabMenu.addSeparator();

            JMenuItem renameItem = new JMenuItem("Rename Tab");
            renameItem.setBorder(new EmptyBorder(5, 10, 5, 10));
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
                deleteItem.setBorder(new EmptyBorder(5, 10, 5, 10));
                deleteItem.addActionListener(e -> {
                    showConfirmationDialog("Delete Tab", "Delete tab '" + tab.name + "' and its items?", () -> {
                        boolean wasActive = (tabs.indexOf(tab) == activeTabIndex);
                        tabs.remove(tab);
                        if (activeTabIndex >= tabs.size()) activeTabIndex = 0;
                        
                        refreshTabsUI();
                        
                        if (wasActive) {
                            // Unload current items first to reduce lag
                            contentPanel.removeAll();
                            cardMap.clear();
                            contentPanel.revalidate();
                            contentPanel.repaint();
                            
                            // Load new items after a short delay
                            Timer t = new Timer(35, ev -> {
                                refreshUI();
                                ((Timer)ev.getSource()).stop();
                            });
                            t.setRepeats(false);
                            t.start();
                        } else {
                            refreshUI();
                        }
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
                                queueTabSwitch(currentIdx);
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
                            int newY = Math.max(0, (getHeight() - tabBtn.getHeight()) / 2);
                            tabBtn.setLocation(newX, newY);
                            
                            // Check for move to new slot based on static positions
                            int slotWidth = currentTabWidth + 4; // width + spacing
                            if (slotWidth > 0) {
                                int centerX = newX + tabBtn.getWidth() / 2;
                                int targetIndex = centerX / slotWidth;
                                targetIndex = Math.max(0, Math.min(targetIndex, tabButtons.size() - 1));
                                
                                if (targetIndex != draggedIndex) {
                                    // Move item in list (not just swap) to preserve order
                                    ClipboardTab draggedTab = tabs.remove(draggedIndex);
                                    tabs.add(targetIndex, draggedTab);
                                    
                                    JButton draggedBtn = tabButtons.remove(draggedIndex);
                                    tabButtons.add(targetIndex, draggedBtn);
                                    
                                    // Update active index
                                    if (activeTabIndex == draggedIndex) {
                                        activeTabIndex = targetIndex;
                                    } else {
                                        // Adjust active index if it was shifted
                                        if (draggedIndex < activeTabIndex && targetIndex >= activeTabIndex) {
                                            activeTabIndex--;
                                        } else if (draggedIndex > activeTabIndex && targetIndex <= activeTabIndex) {
                                            activeTabIndex++;
                                        }
                                    }
                                    
                                    draggedIndex = targetIndex;
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
        String iconValue; // Path to SVG or Emoji text
        boolean isEmoji;

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
    
    // UI Components that need theme updates
    private JLabel titleLabel;
    private FlatSVGIcon searchIcon;
    private FlatSVGIcon trashIcon;
    private JButton clearButton;
    private FlatSVGIcon settingsIcon;
    private JButton settingsButton;

    private OcrService ocrService;
    private ConfigManager configManager = new ConfigManager();

    // Sleek Modern Fonts
    private static final String FONT_FAMILY = "Segoe UI Variable Display";
    private static final String FONT_FAMILY_TEXT = "Segoe UI Variable Text";
    private static final String FONT_FAMILY_TITLE = "Segoe UI Variable Display Semibold";

    // Helper to get font with fallback
    private Font getAppFont(String name, int style, float size) {
        if ("Roboto".equalsIgnoreCase(name) || "Roboto Black".equalsIgnoreCase(name)) {
            String fontFile = "Roboto-Regular.ttf";
            
            if ("Roboto Black".equalsIgnoreCase(name)) {
                fontFile = "Roboto-Black.ttf";
            } else if ((style & Font.BOLD) != 0) {
                fontFile = "Roboto-Bold.ttf";
            }
            // Add other styles if needed, e.g. Condensed, Black

            String cacheKey = fontFile;
            if (fontCache.containsKey(cacheKey)) {
                return fontCache.get(cacheKey).deriveFont(style, size);
            }

            try (InputStream is = getClass().getResourceAsStream("/com/virtualclipboard/fonts/roboto/" + fontFile)) {
                if (is != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    ge.registerFont(font);
                    fontCache.put(cacheKey, font);
                    return font.deriveFont(style, size);
                } else {
                    System.err.println("Could not load font: " + fontFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new Font(name, style, (int) size);
    }

    public App() {
        ocrService = new OcrService();
        updateThemeUIManager();
        updateFormatter();
        setTitle("Clip-Pal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 750);
        setMinimumSize(new Dimension(500, 600));
        setLocationRelativeTo(null);

        Color bgMain = getThemeColor("bgMain");

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
        titleLabel = new JLabel("Clip-Pal");
        titleLabel.setFont(getAppFont("Segoe UI Variable Display Semibold", Font.BOLD, 42));
        titleLabel.setForeground(getThemeColor("textPrimary"));
        brandPanel.add(titleLabel);

        headerPanel.add(brandPanel, BorderLayout.WEST);

        // Search Section
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(200, 35));
        searchField.setBackground(getThemeColor("inputBackground"));
        searchField.setForeground(getThemeColor("inputText"));
        searchField.setCaretColor(getThemeColor("inputText"));
        searchField.setBorder(new EmptyBorder(5, 10, 5, 10));
        searchField.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));

        // Add a placeholder-like behavior or just a label
        searchIcon = new FlatSVGIcon("com/virtualclipboard/icons/tabs/search.svg", 16, 16);
        searchIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
        JLabel searchIconLabel = new JLabel(searchIcon);
        searchIconLabel.setBorder(new EmptyBorder(0, 0, 0, 10));

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchQuery = searchField.getText().toLowerCase();
                refreshUI();
            }
        });

        searchScopeCombo = createStyledComboBox(new String[] { "Current Tab", "All Tabs" }, "Current Tab");
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

        trashIcon = new FlatSVGIcon("com/virtualclipboard/icons/trashcan.svg", 24, 24);
        trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));

        clearButton = new JButton(trashIcon);
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
                trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
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

        settingsIcon = new FlatSVGIcon("com/virtualclipboard/icons/tabs/settings.svg", 24, 24);
        settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
        settingsButton = new JButton(settingsIcon);
        settingsButton.setToolTipText("Settings");
        settingsButton.setOpaque(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setFocusPainted(false);
        settingsButton.setBorder(null);
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textPrimary")));
                settingsButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
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

    private void sortItems(ClipboardTab tab, java.util.Comparator<ClipboardItem> comparator) {
        tab.items.sort(comparator);
        if (tabs.indexOf(tab) == activeTabIndex) {
            refreshUI();
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

            if (configManager.isAutoSortByDate()) {
                sortItems(currentTab, (a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            }

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

    // Tab Queue Logic
    private final java.util.Queue<Integer> tabSwitchQueue = new java.util.LinkedList<>();
    private long lastSwitchTime = 0;
    private boolean isQueueProcessing = false;
    
    // Animation Timers
    private Timer currentFadeOutTimer;
    private Timer currentLoadTimer;
    private final List<Timer> activeFadeInTimers = new ArrayList<>();

    private boolean stopActiveTransitions() {
        boolean interrupted = false;
        if (currentFadeOutTimer != null && currentFadeOutTimer.isRunning()) {
            currentFadeOutTimer.stop();
            interrupted = true;
        }
        if (currentLoadTimer != null && currentLoadTimer.isRunning()) {
            currentLoadTimer.stop();
            interrupted = true;
        }
        if (!activeFadeInTimers.isEmpty()) {
            // Copy list to avoid concurrent modification if needed, but stopping shouldn't trigger modification immediately
            // unless listeners run. But listeners remove themselves.
            // Safest to iterate copy.
            List<Timer> timers = new ArrayList<>(activeFadeInTimers);
            for (Timer t : timers) {
                if (t.isRunning()) {
                    t.stop();
                    interrupted = true;
                }
            }
            activeFadeInTimers.clear();
        }
        return interrupted;
    }

    private void queueTabSwitch(int index) {
        if (tabSwitchQueue.size() >= 3) return;
        tabSwitchQueue.offer(index);
        processTabQueue();
    }

    private void processTabQueue() {
        if (isQueueProcessing) return;

        long now = System.currentTimeMillis();
        long timeSinceLast = now - lastSwitchTime;

        if (timeSinceLast >= 400) {
            performNextQueuedSwitch();
        } else {
            isQueueProcessing = true;
            Timer waitTimer = new Timer((int)(400 - timeSinceLast), e -> {
                ((Timer)e.getSource()).stop();
                performNextQueuedSwitch();
            });
            waitTimer.setRepeats(false);
            waitTimer.start();
        }
    }

    private void performNextQueuedSwitch() {
        if (tabSwitchQueue.isEmpty()) {
            isQueueProcessing = false;
            return;
        }
        
        isQueueProcessing = true;
        Integer nextIndex = tabSwitchQueue.poll();
        if (nextIndex != null) {
            lastSwitchTime = System.currentTimeMillis();
            executeTabSwitch(nextIndex);
            
            Timer nextTimer = new Timer(400, e -> {
                ((Timer)e.getSource()).stop();
                performNextQueuedSwitch();
            });
            nextTimer.setRepeats(false);
            nextTimer.start();
        } else {
            isQueueProcessing = false;
        }
    }

    private void executeTabSwitch(int index) {
        boolean interrupted = stopActiveTransitions(); // Interrupt any ongoing transition

        if (activeTabIndex == index) return;
        activeTabIndex = index;
        refreshTabsUI();
        
        // If interrupted, skip fade out to immediately load new tab
        if (interrupted) {
            performTabSwitch();
            return;
        }

        // Animate out existing cards
        List<AnimatedCard> cardsToAnimate = new ArrayList<>(cardMap.values());
        if (!cardsToAnimate.isEmpty()) {
            currentFadeOutTimer = new Timer(10, null);
            final float[] alpha = { 1.0f };
            currentFadeOutTimer.addActionListener(e -> {
                alpha[0] -= 0.22f; // Fast fade out (20% faster)
                if (alpha[0] <= 0.0f) {
                    ((Timer)e.getSource()).stop();
                    performTabSwitch();
                } else {
                    for (AnimatedCard card : cardsToAnimate) {
                        card.setAlpha(alpha[0]);
                    }
                    contentPanel.repaint();
                }
            });
            currentFadeOutTimer.start();
        } else {
            performTabSwitch();
        }
    }

    private void performTabSwitch() {
        // Unload current items first to reduce lag
        contentPanel.removeAll();
        cardMap.clear();
        contentPanel.revalidate();
        contentPanel.repaint();
        
        // Load new items after a short delay
        currentLoadTimer = new Timer(28, e -> {
            refreshUI();
            ((Timer)e.getSource()).stop();
        });
        currentLoadTimer.setRepeats(false);
        currentLoadTimer.start();
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
                activeFadeInTimers.add(t); // Track timer
                final float[] alpha = { 0.0f };
                t.addActionListener(e -> {
                    alpha[0] += 0.07f; // Faster fade in (20% faster)
                    if (alpha[0] >= 1.0f) {
                        alpha[0] = 1.0f;
                        t.stop();
                        activeFadeInTimers.remove(t);
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
                        ClipboardTab tab = (ClipboardTab) obj;
                        if (configManager.isAutoSortByDate()) {
                            tab.items.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
                        }
                        tabs.add(tab);
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
        
        boolean dynamicResizing = configManager.isDynamicResizing();
        int itemCols = dynamicResizing ? Math.min(item.getCols(), cols) : 1;
        int itemRows = dynamicResizing ? item.getRows() : 1;
        
        int cardWidth = baseCardWidth * itemCols + (itemCols - 1) * 20;
        int cardHeight = baseCardHeight * itemRows + (itemRows - 1) * 20;

        AnimatedCard card = new AnimatedCard(new BorderLayout(10, 5));
        card.setPreferredSize(new Dimension(cardWidth, cardHeight));
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Header Section (Time + Controls)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel time = new JLabel(item.getTimestamp().format(formatter));
        time.setFont(getAppFont("Roboto", Font.PLAIN, 14));
        time.setForeground(getThemeColor("cardText"));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlPanel.setOpaque(false);

        JButton infoBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/tabs/info.svg", 16, 16), "cardIcon", "cardIconHover");
        JButton deleteBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/close.svg", 16, 16), "cardIcon", "cardIconHover");

        infoBtn.addActionListener(e -> showInfoPopup(item));
        deleteBtn.addActionListener(e -> {
            showConfirmationDialog("Delete Item", "Are you sure you want to delete this item?",
                    () -> deleteEntry(item, card));
        });

        // Move Button
        JButton moveBtn = createSubtleButton(new FlatSVGIcon("com/virtualclipboard/icons/move.svg", 16, 16), "cardIcon", "cardIconHover");
        moveBtn.setToolTipText("Move to another tab");
        moveBtn.addActionListener(e -> {
            JPopupMenu moveMenu = new JPopupMenu();
            moveMenu.setBorder(new EmptyBorder(0,0,0,0));
            for (int i = 0; i < tabs.size(); i++) {
                if (i == activeTabIndex)
                    continue;
                ClipboardTab targetTab = tabs.get(i);
                JMenuItem menuItem = new JMenuItem("Move to " + targetTab.name);
                menuItem.setBorder(new EmptyBorder(5, 10, 5, 10));
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
                empty.setBorder(new EmptyBorder(5, 10, 5, 10));
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
        JLabel typeIndicator;
        if (configManager.isUseSvgTypeIcons()) {
            String iconName = "Text.svg";
            if (displayType == ClipboardItem.Type.IMAGE) iconName = "Image.svg";
            else if (displayType == ClipboardItem.Type.URL) iconName = "URL.svg";
            else if (displayType == ClipboardItem.Type.SVG) iconName = "SVG.svg";
            else if (displayType == ClipboardItem.Type.GIF) iconName = "GIF.svg";
            
            FlatSVGIcon typeIcon = new FlatSVGIcon("com/virtualclipboard/icons/entries/" + iconName, 16, 16);
            typeIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("cardIcon")));
            typeIndicator = new JLabel(typeIcon);
        } else {
            String typeStr = "T";
            if (displayType == ClipboardItem.Type.IMAGE) typeStr = "I";
            else if (displayType == ClipboardItem.Type.URL) typeStr = "U";
            else if (displayType == ClipboardItem.Type.SVG) typeStr = "S";
            else if (displayType == ClipboardItem.Type.GIF) typeStr = "G";
            
            typeIndicator = new JLabel(typeStr);
            typeIndicator.setFont(getAppFont(FONT_FAMILY, Font.BOLD, configManager.getFontSize() + 3));
            typeIndicator.setForeground(getThemeColor("cardIcon"));
        }

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
        timeLabel.setForeground(getThemeColor("cardText"));

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
                    timeLabel.setForeground(getThemeColor("cardText"));
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
        dialog.getContentPane().setBackground(getThemeColor("bgMain")); // Match App BG

        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel msgLabel = new JLabel(
                "<html><div style='width:250px'>" + message.replace("\n", "<br>") + "</div></html>");
        msgLabel.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 15));
        msgLabel.setForeground(getThemeColor("textPrimary"));
        panel.add(msgLabel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(getThemeColor("buttonBackground"));
        cancelBtn.setForeground(getThemeColor("buttonText"));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton confirmBtn = new JButton("Confirm");
        confirmBtn.setBackground(new Color(255, 60, 60)); // Red for destructive
        confirmBtn.setForeground(getThemeColor("buttonText"));
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

    private JButton createSubtleButton(Icon icon, String colorKey, String hoverKey) {
        JButton btn = new JButton(icon);
        if (icon instanceof FlatSVGIcon) {
            ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor(colorKey)));
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
                    ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor(hoverKey)));
                    btn.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (icon instanceof FlatSVGIcon) {
                    ((FlatSVGIcon) icon).setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor(colorKey)));
                    btn.repaint();
                }
            }
        });

        return btn;
    }

    private JButton createSubtleButton(Icon icon) {
        return createSubtleButton(icon, "textSecondary", "textPrimary");
    }

    private void showIconSelector(ClipboardTab tab) {
        JDialog dialog = new JDialog(this, "Select Icon", true);
        dialog.setSize(440, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(getThemeColor("bgMain"));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Icons Panel
        JPanel iconsPanel = new JPanel(new GridLayout(0, 5, 15, 15));
        iconsPanel.setOpaque(false);
        iconsPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        
        String[] iconNames = {
            "home", "work", "star", "heart", "code", "chat", "search", "settings", 
            "info", "edit", "image", "folder", "link", "check", "music", "video",
            "document", "download", "upload", "cloud", "mail", "calendar", "user", 
            "lock", "globe", "bookmark", "clipboard", "console", "chart", "game"
        };
        for (String name : iconNames) {
            String path = "com/virtualclipboard/icons/tabs/" + name + ".svg";
            JButton btn = createIconSelectorButton(path, 28);
            btn.addActionListener(e -> {
                tab.iconValue = path;
                tab.isEmoji = false;
                saveClipboardState();
                refreshTabsUI();
                dialog.dispose();
            });
            iconsPanel.add(btn);
        }
        
        JPanel iconsWrapper = new JPanel(new BorderLayout());
        iconsWrapper.setOpaque(false);
        JScrollPane iconsScroll = new JScrollPane(iconsPanel);
        iconsScroll.setOpaque(false);
        iconsScroll.getViewport().setOpaque(false);
        iconsScroll.setBorder(null);
        iconsWrapper.add(iconsScroll, BorderLayout.CENTER);
        tabs.addTab("Icons", iconsWrapper);

        // Emojis Panel
        JPanel emojisPanel = new JPanel(new GridLayout(0, 5, 10, 10));
        emojisPanel.setOpaque(false);
        emojisPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        String[] emojis = {"😀", "😂", "😍", "🚀", "💡", "🔥", "👍", "🎉", "📅", "✅", "⚡", "💻", "🎮", "🎵", "📷", "🍔", "🍺", "✈️", "🏠", "❤️"};
        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
            btn.setForeground(getThemeColor("textSecondary"));
            btn.setContentAreaFilled(false);
            btn.setBorder(new LineBorder(getThemeColor("textSecondary"), 1, true));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(50, 50));
            
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setBorder(new LineBorder(getThemeColor("textPrimary"), 1, true));
                    btn.setForeground(getThemeColor("textPrimary"));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setBorder(new LineBorder(getThemeColor("textSecondary"), 1, true));
                    btn.setForeground(getThemeColor("textSecondary"));
                }
            });

            btn.addActionListener(e -> {
                tab.iconValue = emoji;
                tab.isEmoji = true;
                saveClipboardState();
                refreshTabsUI();
                dialog.dispose();
            });
            emojisPanel.add(btn);
        }
        
        JPanel emojisWrapper = new JPanel(new BorderLayout());
        emojisWrapper.setOpaque(false);
        JScrollPane emojisScroll = new JScrollPane(emojisPanel);
        emojisScroll.setOpaque(false);
        emojisScroll.getViewport().setOpaque(false);
        emojisScroll.setBorder(null);
        emojisWrapper.add(emojisScroll, BorderLayout.CENTER);
        tabs.addTab("Emojis", emojisWrapper);

        // Custom Input Panel
        JPanel customPanel = new JPanel(new BorderLayout(15, 15));
        customPanel.setOpaque(false);
        customPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JTextField customField = new JTextField();
        customField.putClientProperty("JTextField.placeholderText", "Enter custom emoji or text...");
        customField.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
        customField.putClientProperty("JTextField.showClearButton", true);
        customField.setBackground(getThemeColor("inputBackground"));
        customField.setForeground(getThemeColor("inputText"));
        customField.setCaretColor(getThemeColor("inputText"));
        
        JButton setBtn = new JButton("Apply Custom");
        setBtn.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        setBtn.setBackground(getThemeColor("buttonBackground"));
        setBtn.setForeground(getThemeColor("buttonText"));
        setBtn.setFocusPainted(false);
        setBtn.addActionListener(e -> {
            String text = customField.getText().trim();
            if (!text.isEmpty()) {
                tab.iconValue = text;
                tab.isEmoji = true;
                saveClipboardState();
                refreshTabsUI();
                dialog.dispose();
            }
        });

        JButton removeBtn = new JButton("Remove Icon");
        removeBtn.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        removeBtn.setBackground(new Color(255, 60, 60));
        removeBtn.setForeground(getThemeColor("buttonText"));
        removeBtn.setFocusPainted(false);
        removeBtn.addActionListener(e -> {
            tab.iconValue = null;
            tab.isEmoji = false;
            saveClipboardState();
            refreshTabsUI();
            dialog.dispose();
        });

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(setBtn);
        btnPanel.add(removeBtn);

        customPanel.add(customField, BorderLayout.NORTH);
        customPanel.add(btnPanel, BorderLayout.SOUTH);
        
        // Combine Custom Panel with a label explanation or just add it to dialog
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setOpaque(false);
        bottomContainer.add(customPanel, BorderLayout.NORTH);
        
        dialog.add(tabs, BorderLayout.CENTER);
        dialog.add(bottomContainer, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    private JButton createIconSelectorButton(String iconPath, int size) {
        FlatSVGIcon icon = new FlatSVGIcon(iconPath, size, size);
        Color defaultColor = getThemeColor("textSecondary");
        Color hoverColor = getThemeColor("textPrimary");
        
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> defaultColor));
        
        JButton btn = new JButton(icon);
        btn.setPreferredSize(new Dimension(60, 60));
        btn.setContentAreaFilled(false);
        btn.setBorder(new LineBorder(defaultColor, 1, true));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> hoverColor));
                btn.setBorder(new LineBorder(hoverColor, 1, true));
                btn.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> defaultColor));
                btn.setBorder(new LineBorder(defaultColor, 1, true));
                btn.repaint();
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
        JDialog dialog = new JDialog(this, "Item Details", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.getContentPane().setBackground(getThemeColor("bgMain"));

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
            
            if (item.getType() != ClipboardItem.Type.URL) {
                details.add(new String[] { "Words", String.valueOf(item.getWordCount()) });
                details.add(new String[] { "Lines", String.valueOf(item.getLineCount()) });
            }
            
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
            
            if (item.getType() == ClipboardItem.Type.GIF && item.getFrameCount() > 1) {
                details.add(new String[] { "Frames", String.valueOf(item.getFrameCount()) });
                details.add(new String[] { "Duration", item.getFormattedDuration() });
            }
        }

        for (String[] detail : details) {
            JLabel key = new JLabel(detail[0]);
            key.setForeground(getThemeColor("textSecondary"));
            key.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));

            JLabel val = new JLabel(detail[1]);
            val.setForeground(getThemeColor("generalText"));
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
            textArea.setBackground(getThemeColor("inputBackground"));
            
            if (item.getType() == ClipboardItem.Type.URL) {
                if (configManager.isHighContrast()) {
                    textArea.setForeground(getThemeColor("inputText"));
                } else {
                    textArea.setForeground(getThemeColor("accent"));
                }
                Map<TextAttribute, Object> attributes = new HashMap<>();
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                textArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16).deriveFont(attributes));
            } else {
                textArea.setForeground(getThemeColor("inputText"));
                textArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 16));
            }
            
            textArea.setCaretColor(getThemeColor("inputText"));
            textArea.setBorder(new EmptyBorder(15, 15, 15, 15));

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(500, 300));
            scroll.setBorder(new LineBorder(getThemeColor("textSecondary")));
            JPanel textPanel = new JPanel(new BorderLayout(0, 10));
            textPanel.setOpaque(false);
            textPanel.add(scroll, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            btnPanel.setOpaque(false);
            
            JButton searchBtn = new JButton("Search Web");
            searchBtn.setBackground(getThemeColor("buttonBackground"));
            searchBtn.setForeground(getThemeColor("buttonText"));
            searchBtn.setFocusPainted(false);
            searchBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
            searchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            searchBtn.addActionListener(e -> {
                 String query = item.getText();
                 String engine = configManager.getTextSearchEngine();
                 String url = "";
                 try {
                     String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
                     switch (engine) {
                         case "Bing": url = "https://www.bing.com/search?q=" + encodedQuery; break;
                         case "DuckDuckGo": url = "https://duckduckgo.com/?q=" + encodedQuery; break;
                         case "Yahoo": url = "https://search.yahoo.com/search?p=" + encodedQuery; break;
                         case "Yandex": url = "https://yandex.com/search/?text=" + encodedQuery; break;
                         case "Google": default: url = "https://www.google.com/search?q=" + encodedQuery; break;
                     }
                     openBrowser(url);
                 } catch (Exception ex) {
                     ex.printStackTrace();
                 }
            });
            
            btnPanel.add(searchBtn);
            textPanel.add(btnPanel, BorderLayout.SOUTH);
            
            mainPanel.add(textPanel, BorderLayout.CENTER);
        } else {
            JPanel imageContainer = new JPanel(new BorderLayout(10, 10));
            imageContainer.setOpaque(false);

            JLabel imgLabel = new JLabel();
            int imgW = 0;
            int imgH = 0;

            if (item.getType() == ClipboardItem.Type.SVG && svgIcon != null) {
                // Display SVG at full size
                imgLabel.setIcon(svgIcon);
                imgW = svgIcon.getIconWidth();
                imgH = svgIcon.getIconHeight();
            } else if (item.getType() == ClipboardItem.Type.GIF) {
                // Use standard ImageIcon to preserve animation
                if (item.getGifData() != null) {
                    ImageIcon icon = new ImageIcon(item.getGifData());
                    imgLabel.setIcon(icon);
                    imgW = icon.getIconWidth();
                    imgH = icon.getIconHeight();
                }
            } else if (item.getType() == ClipboardItem.Type.IMAGE) {
                // Display standard image at full size
                ImageIcon icon = new ImageIcon(item.getImage());
                imgLabel.setIcon(icon);
                imgW = icon.getIconWidth();
                imgH = icon.getIconHeight();
            }
            imgLabel.setHorizontalAlignment(JLabel.CENTER);

            // Wrap in ScrollPane with max size cap (50% of screen)
            JScrollPane scroll = new JScrollPane(imgLabel);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setOpaque(false);
            scroll.setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(30);
            scroll.getHorizontalScrollBar().setUnitIncrement(30);

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = (int) (screenSize.width * 0.5);
            int maxH = (int) (screenSize.height * 0.5);

            // Calculate preferred size: image size + padding for scrollbars, capped at 50% screen
            int prefW = Math.min(maxW, imgW + 30);
            int prefH = Math.min(maxH, imgH + 30);
            
            // Ensure minimum size for visibility
            prefW = Math.max(prefW, 200);
            prefH = Math.max(prefH, 150);

            scroll.setPreferredSize(new Dimension(prefW, prefH));
            JComponent centerComponent = scroll;

            imageContainer.add(centerComponent, BorderLayout.CENTER);

            // OCR Action Panel
            JPanel ocrPanel = new JPanel(new BorderLayout(10, 10));
            ocrPanel.setOpaque(false);
            ocrPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            btnPanel.setOpaque(false);

            JButton scanBtn = new JButton("Scan Text (OCR)");
            scanBtn.setBackground(getThemeColor("buttonBackground"));
            scanBtn.setForeground(getThemeColor("buttonText"));
            scanBtn.setFocusPainted(false);
            scanBtn.setBorder(new EmptyBorder(8, 15, 8, 15));

            JButton searchBtn = new JButton("Visual Search");
            searchBtn.setBackground(getThemeColor("buttonBackground"));
            searchBtn.setForeground(getThemeColor("buttonText"));
            searchBtn.setFocusPainted(false);
            searchBtn.setBorder(new EmptyBorder(8, 15, 8, 15));

            btnPanel.add(scanBtn);
            btnPanel.add(searchBtn);

            JTextArea resultArea = new JTextArea();
            resultArea.setEditable(false);
            resultArea.setLineWrap(true);
            resultArea.setWrapStyleWord(true);
            resultArea.setBackground(getThemeColor("inputBackground"));
            resultArea.setForeground(getThemeColor("inputText"));
            resultArea.setFont(getAppFont(FONT_FAMILY_TEXT, Font.PLAIN, 14));
            resultArea.setBorder(new EmptyBorder(10, 10, 10, 10));
            resultArea.setVisible(false);

            JScrollPane resultScroll = new JScrollPane(resultArea);
            resultScroll.setPreferredSize(new Dimension(500, 150));
            resultScroll.setBorder(new LineBorder(getThemeColor("textSecondary")));
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
                                copyOcrBtn.setBackground(getThemeColor("buttonBackground"));
                                copyOcrBtn.setForeground(getThemeColor("buttonText"));
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
        closeBtn.setBackground(getThemeColor("buttonBackground"));
        closeBtn.setForeground(getThemeColor("buttonText"));
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
        String[] themes = { 
            "Dark", "Deep Ocean", "Forest", "Sunset", 
            "Twilight", "Neon Night", 
            "Paper White", "Soft Mint", "Lavender Mist" 
        };
        JComboBox<String> themeCombo = createStyledComboBox(themes, configManager.getTheme());
        contentPanel.add(themeCombo);
        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createSettingLabel("Font Size", textSecondary));
        Integer[] fontSizes = { 12, 14, 16, 18, 20 };
        JComboBox<Integer> fontCombo = createStyledComboBox(fontSizes, configManager.getFontSize());
        contentPanel.add(fontCombo);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Use SVG Icons (Moved to Appearance)
        JCheckBox useSvgIconsCheck = createSettingCheckbox("Use SVG Type Icons", configManager.isUseSvgTypeIcons(), textPrimary);
        contentPanel.add(useSvgIconsCheck);
        contentPanel.add(Box.createVerticalStrut(10));
        
        JCheckBox dynamicResizingCheck = createSettingCheckbox("Dynamic Card Resizing", configManager.isDynamicResizing(), textPrimary);
        contentPanel.add(dynamicResizingCheck);
        contentPanel.add(Box.createVerticalStrut(10));

        JCheckBox highContrastCheck = createSettingCheckbox("High Contrast Colors", configManager.isHighContrast(), textPrimary);
        contentPanel.add(highContrastCheck);
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

        contentPanel.add(createSettingLabel("Text Search Engine", textSecondary));
        String[] textSearchEngines = { "Google", "Bing", "DuckDuckGo", "Yahoo", "Yandex" };
        JComboBox<String> textSearchEngineCombo = createStyledComboBox(textSearchEngines, configManager.getTextSearchEngine());
        contentPanel.add(textSearchEngineCombo);
        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createSettingLabel("Image Search Engine", textSecondary));
        String[] searchEngines = { "Google", "Yandex", "Bing", "TinEye", "SauceNAO" };
        JComboBox<String> searchEngineCombo = createStyledComboBox(searchEngines, configManager.getSearchEngine());
        contentPanel.add(searchEngineCombo);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Use Incognito Mode (Moved to Search & Tools)
        JCheckBox incognitoCheck = createSettingCheckbox("Use Incognito Mode", configManager.isIncognito(), textPrimary);
        contentPanel.add(incognitoCheck);
        contentPanel.add(Box.createVerticalStrut(30));

        // Group 3: History
        contentPanel.add(createSectionHeader("History", accent));
        
        contentPanel.add(createSettingLabel("Max History Items", textSecondary));
        Integer[] historyLimits = { 25, 50, 100, 200, 500 };
        JComboBox<Integer> historyCombo = createStyledComboBox(historyLimits, configManager.getMaxHistory());
        contentPanel.add(historyCombo);
        contentPanel.add(Box.createVerticalStrut(30));
        
        // Group 4: Other
        contentPanel.add(createSectionHeader("Other", accent));

        // Switches
        JCheckBox autoStartCheck = createSettingCheckbox("Start with Windows", configManager.isAutoStart(), textPrimary);
        JCheckBox autoSortCheck = createSettingCheckbox("Auto-sort by Date (Newest First)", configManager.isAutoSortByDate(), textPrimary);
        JCheckBox use24HourTimeCheck = createSettingCheckbox("Use 24-Hour Time", configManager.isUse24HourTime(), textPrimary);

        contentPanel.add(autoStartCheck);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(autoSortCheck);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(use24HourTimeCheck);
        
        contentPanel.add(Box.createVerticalStrut(40));

        // Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn = new JButton("Save & Apply");
        saveBtn.setBackground(getThemeColor("buttonBackground"));
        saveBtn.setForeground(getThemeColor("buttonText"));
        saveBtn.setFocusPainted(false);
        saveBtn.setFont(getAppFont(FONT_FAMILY_TEXT, Font.BOLD, 14));
        saveBtn.setBorder(new EmptyBorder(12, 25, 12, 25));
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        saveBtn.addActionListener(e -> {
            configManager.setBrowser((String) browserCombo.getSelectedItem());
            configManager.setTextSearchEngine((String) textSearchEngineCombo.getSelectedItem());
            configManager.setSearchEngine((String) searchEngineCombo.getSelectedItem());
            configManager.setTheme((String) themeCombo.getSelectedItem());
            configManager.setFontSize((Integer) fontCombo.getSelectedItem());
            configManager.setMaxHistory((Integer) historyCombo.getSelectedItem());
            configManager.setIncognito(incognitoCheck.isSelected());
            configManager.setAutoStart(autoStartCheck.isSelected());
            configManager.setAutoSortByDate(autoSortCheck.isSelected());
            configManager.setUseSvgTypeIcons(useSvgIconsCheck.isSelected());
            configManager.setDynamicResizing(dynamicResizingCheck.isSelected());
            configManager.setUse24HourTime(use24HourTimeCheck.isSelected());
            configManager.setHighContrast(highContrastCheck.isSelected());
            
            if (configManager.isAutoSortByDate()) {
                 for (ClipboardTab t : tabs) {
                     t.items.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
                 }
            }

            configManager.save();
            applySettings();
            refreshUI();
            dialog.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(getThemeColor("inputBackground"));
        if (configManager.isHighContrast()) {
            Color bg = getThemeColor("inputBackground");
            Color btnText = getThemeColor("buttonText");
            
            // Calculate brightness
            double bgBrightness = (bg.getRed() * 0.299) + (bg.getGreen() * 0.587) + (bg.getBlue() * 0.114);
            double textBrightness = (btnText.getRed() * 0.299) + (btnText.getGreen() * 0.587) + (btnText.getBlue() * 0.114);
            
            // Use buttonText if it has enough contrast with background
            if (Math.abs(bgBrightness - textBrightness) > 100) {
                cancelBtn.setForeground(btnText);
            } else {
                cancelBtn.setForeground(getThemeColor("inputText"));
            }
        } else {
            cancelBtn.setForeground(getThemeColor("textSecondary"));
        }
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
        label.setFont(getAppFont("Roboto Black", Font.PLAIN, 14));
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
        combo.setBackground(getThemeColor("inputBackground"));
        combo.setForeground(getThemeColor("inputText"));
        combo.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        // Custom Renderer to ensure theme colors are applied to the dropdown list
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (isSelected) {
                    if (configManager.isHighContrast()) {
                        setBackground(getThemeColor("bgMain"));
                        setForeground(getThemeColor("accent"));
                    } else {
                        setBackground(getThemeColor("accent"));
                        setForeground(getThemeColor("buttonText"));
                    }
                } else {
                    setBackground(getThemeColor("inputBackground"));
                    setForeground(getThemeColor("inputText"));
                }
                setBorder(new EmptyBorder(5, 10, 5, 10));
                return this;
            }
        });
        
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
        if (configManager.isHighContrast()) {
            if ("inputBackground".equals(key)) return getThemeColor("accent");
            if ("inputText".equals(key)) {
                Color accent = getThemeColor("accent");
                // Calculate brightness to ensure text is visible on accent background
                // Formula: (R*299 + G*587 + B*114) / 1000
                double brightness = (accent.getRed() * 0.299) + (accent.getGreen() * 0.587) + (accent.getBlue() * 0.114);
                return brightness < 128 ? Color.WHITE : Color.BLACK;
            }
        }
        String theme = configManager.getTheme();
        switch (theme) {
            case "Deep Ocean":
                if ("bgMain".equals(key)) return new Color(10, 25, 40);
                if ("accent".equals(key)) return new Color(60, 160, 255);
                if ("textPrimary".equals(key)) return Color.WHITE;
                if ("textSecondary".equals(key)) return new Color(150, 180, 210);
                if ("cardText".equals(key)) return Color.WHITE;
                if ("inputBackground".equals(key)) return new Color(30, 65, 95);
                if ("inputText".equals(key)) return Color.WHITE;
                if ("buttonBackground".equals(key)) return new Color(60, 160, 255);
                if ("buttonText".equals(key)) return Color.WHITE;
                if ("generalText".equals(key)) return Color.WHITE;
                break;
            case "Forest":
                if ("bgMain".equals(key)) return new Color(15, 30, 20);
                if ("accent".equals(key)) return new Color(80, 200, 120);
                if ("textPrimary".equals(key)) return new Color(230, 245, 230);
                if ("textSecondary".equals(key)) return new Color(140, 170, 145);
                if ("cardText".equals(key)) return Color.WHITE;
                if ("inputBackground".equals(key)) return new Color(35, 70, 50);
                if ("inputText".equals(key)) return new Color(230, 245, 230);
                if ("buttonBackground".equals(key)) return new Color(80, 200, 120);
                if ("buttonText".equals(key)) return Color.WHITE;
                if ("generalText".equals(key)) return new Color(230, 245, 230);
                break;
            case "Sunset":
                if ("bgMain".equals(key)) return new Color(40, 20, 30);
                if ("accent".equals(key)) return new Color(255, 100, 100);
                if ("textPrimary".equals(key)) return new Color(255, 240, 240);
                if ("textSecondary".equals(key)) return new Color(210, 160, 170);
                if ("cardText".equals(key)) return Color.WHITE;
                if ("inputBackground".equals(key)) return new Color(95, 50, 65);
                if ("inputText".equals(key)) return new Color(255, 240, 240);
                if ("buttonBackground".equals(key)) return new Color(255, 100, 100);
                if ("buttonText".equals(key)) return Color.WHITE;
                if ("generalText".equals(key)) return new Color(255, 240, 240);
                break;
            case "Twilight":
                if ("bgMain".equals(key)) return new Color(20, 15, 40); // Dark Purple/Blue
                if ("accent".equals(key)) return new Color(138, 43, 226); // Blue Violet
                if ("textPrimary".equals(key)) return new Color(240, 230, 255); // Lavender
                if ("textSecondary".equals(key)) return new Color(180, 160, 200);
                if ("cardText".equals(key)) return new Color(30, 10, 40); // Deep Purple
                if ("inputBackground".equals(key)) return new Color(250, 245, 255);
                if ("inputText".equals(key)) return new Color(30, 10, 40);
                if ("buttonBackground".equals(key)) return new Color(140, 80, 200); // Purple Button
                if ("buttonText".equals(key)) return Color.WHITE;
                if ("generalText".equals(key)) return Color.WHITE;
                if ("cardIcon".equals(key)) return new Color(60, 30, 80); // Deep Purple for icons on light card
                if ("cardIconHover".equals(key)) return new Color(100, 60, 140); // Lighter Purple on hover
                break;
            case "Neon Night":
                if ("bgMain".equals(key)) return new Color(5, 5, 5);
                if ("accent".equals(key)) return new Color(0, 255, 0);
                if ("textPrimary".equals(key)) return new Color(0, 255, 0);
                if ("textSecondary".equals(key)) return new Color(0, 150, 0);
                if ("cardText".equals(key)) return new Color(0, 255, 0);
                if ("inputBackground".equals(key)) return new Color(20, 20, 20);
                if ("inputText".equals(key)) return new Color(0, 255, 0);
                if ("buttonBackground".equals(key)) return new Color(0, 255, 0);
                if ("buttonText".equals(key)) return Color.BLACK;
                if ("generalText".equals(key)) return new Color(0, 255, 0);
                break;
            case "Paper White":
                if ("bgMain".equals(key)) return new Color(245, 245, 245);
                if ("accent".equals(key)) return new Color(50, 50, 50);
                if ("textPrimary".equals(key)) return new Color(20, 20, 20);
                if ("textSecondary".equals(key)) return new Color(80, 80, 80);
                if ("cardText".equals(key)) return new Color(20, 20, 20);
                if ("inputBackground".equals(key)) return Color.WHITE;
                if ("inputText".equals(key)) return Color.BLACK;
                if ("buttonBackground".equals(key)) return new Color(220, 220, 220);
                if ("buttonText".equals(key)) return Color.BLACK;
                if ("generalText".equals(key)) return Color.BLACK;
                break;
            case "Soft Mint":
                if ("bgMain".equals(key)) return new Color(224, 242, 241);
                if ("accent".equals(key)) return new Color(0, 150, 136);
                if ("textPrimary".equals(key)) return new Color(0, 77, 64);
                if ("textSecondary".equals(key)) return new Color(0, 121, 107);
                if ("cardText".equals(key)) return new Color(0, 77, 64);
                if ("inputBackground".equals(key)) return new Color(250, 255, 255);
                if ("inputText".equals(key)) return new Color(0, 77, 64);
                if ("buttonBackground".equals(key)) return new Color(178, 223, 219);
                if ("buttonText".equals(key)) return new Color(0, 77, 64);
                if ("generalText".equals(key)) return new Color(0, 77, 64);
                break;
            case "Lavender Mist":
                if ("bgMain".equals(key)) return new Color(243, 229, 245);
                if ("accent".equals(key)) return new Color(156, 39, 176);
                if ("textPrimary".equals(key)) return new Color(74, 20, 140);
                if ("textSecondary".equals(key)) return new Color(106, 27, 154);
                if ("cardText".equals(key)) return new Color(74, 20, 140);
                if ("inputBackground".equals(key)) return new Color(255, 250, 255);
                if ("inputText".equals(key)) return new Color(74, 20, 140);
                if ("buttonBackground".equals(key)) return new Color(225, 190, 231);
                if ("buttonText".equals(key)) return new Color(74, 20, 140);
                if ("generalText".equals(key)) return new Color(74, 20, 140);
                break;
            default: // Dark
                if ("bgMain".equals(key)) return new Color(18, 18, 20);
                if ("accent".equals(key)) return new Color(60, 120, 255);
                if ("textPrimary".equals(key)) return Color.WHITE;
                if ("textSecondary".equals(key)) return new Color(180, 180, 190);
                if ("cardText".equals(key)) return Color.WHITE;
                if ("inputBackground".equals(key)) return new Color(45, 45, 52);
                if ("inputText".equals(key)) return Color.WHITE;
                if ("buttonBackground".equals(key)) return new Color(60, 120, 255);
                if ("buttonText".equals(key)) return Color.WHITE;
                if ("generalText".equals(key)) return Color.WHITE;
                break;
        }

        if ("cardIcon".equals(key)) return getThemeColor("textSecondary");
        if ("cardIconHover".equals(key)) return getThemeColor("textPrimary");

        return Color.WHITE;
    }

    private void updateThemeUIManager() {
        Color bgMain = getThemeColor("bgMain");
        Color accent = getThemeColor("accent");
        Color inputBg = getThemeColor("inputBackground");
        Color textPrimary = getThemeColor("textPrimary");
        Color buttonText = getThemeColor("buttonText");
        Color textSecondary = getThemeColor("textSecondary");
        Color inputText = getThemeColor("inputText");

        Color selectionBg = configManager.isHighContrast() ? bgMain : accent;
        Color selectionFg = configManager.isHighContrast() ? accent : buttonText;
        Color focusBorder = configManager.isHighContrast() ? buttonText : accent;

        // Set Global Accent for FlatLaf
        UIManager.put("Component.accentColor", accent);
        UIManager.put("Component.focusColor", focusBorder);
        UIManager.put("Component.borderColor", new Color(0,0,0,0));

        // Popup Menu & Popups
        UIManager.put("PopupMenu.background", inputBg);
        UIManager.put("PopupMenu.foreground", inputText);
        UIManager.put("PopupMenu.borderColor", new Color(0,0,0,0));
        UIManager.put("PopupMenu.border", BorderFactory.createEmptyBorder());
        UIManager.put("Popup.borderColor", new Color(0,0,0,0)); // Critical for FlatLaf popups
        UIManager.put("Popup.border", BorderFactory.createEmptyBorder());
        UIManager.put("Popup.background", inputBg);

        // Menu Item
        UIManager.put("MenuItem.background", inputBg);
        UIManager.put("MenuItem.foreground", inputText);
        UIManager.put("MenuItem.selectionBackground", selectionBg);
        UIManager.put("MenuItem.selectionForeground", selectionFg);
        UIManager.put("MenuItem.border", new EmptyBorder(5, 10, 5, 10)); // Add padding
        
        // Menu (Submenus)
        UIManager.put("Menu.background", inputBg);
        UIManager.put("Menu.foreground", inputText);
        UIManager.put("Menu.selectionBackground", selectionBg);
        UIManager.put("Menu.selectionForeground", selectionFg);
        UIManager.put("Menu.border", new EmptyBorder(5, 10, 5, 10)); // Add padding & fix alignment
        UIManager.put("Menu.margin", new Insets(0, 0, 0, 0)); // Reset margin to let border handle spacing
        
        // ComboBox & List (Dropdowns)
        UIManager.put("ComboBox.selectionBackground", selectionBg);
        UIManager.put("ComboBox.selectionForeground", selectionFg);
        UIManager.put("ComboBox.background", inputBg);
        UIManager.put("ComboBox.foreground", inputText);
        UIManager.put("ComboBox.popupBackground", inputBg);
        UIManager.put("ComboBox.popupBorderColor", new Color(0,0,0,0)); // Explicit popup border
        UIManager.put("ComboBox.popupBorder", BorderFactory.createEmptyBorder());
        UIManager.put("ComboPopup.border", BorderFactory.createEmptyBorder());
        UIManager.put("ComboBox.borderColor", new Color(0,0,0,0));
        UIManager.put("ComboBox.focusedBorderColor", focusBorder);
        UIManager.put("ComboBox.hoverBorderColor", inputText);
        UIManager.put("ComboBox.buttonArrowColor", textSecondary);
        UIManager.put("ComboBox.buttonEditableBackground", inputBg);
        UIManager.put("ComboBox.buttonBackground", inputBg);
        
        UIManager.put("List.background", inputBg);
        UIManager.put("List.foreground", inputText);
        UIManager.put("List.selectionBackground", selectionBg);
        UIManager.put("List.selectionForeground", selectionFg);
        
        // CheckBox & RadioButton Menu Items
        UIManager.put("CheckBoxMenuItem.background", inputBg);
        UIManager.put("CheckBoxMenuItem.foreground", inputText);
        UIManager.put("CheckBoxMenuItem.selectionBackground", selectionBg);
        UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);

        UIManager.put("RadioButtonMenuItem.background", inputBg);
        UIManager.put("RadioButtonMenuItem.foreground", inputText);
        UIManager.put("RadioButtonMenuItem.selectionBackground", selectionBg);
        UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
        
        // Separator
        UIManager.put("PopupMenu.separatorColor", textSecondary);
    }

    private void updateFormatter() {
        if (configManager.isUse24HourTime()) {
            formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");
        } else {
            formatter = DateTimeFormatter.ofPattern("MMM dd, hh:mm:ss a");
        }
    }

    private void applySettings() {
        updateFormatter();
        Color bgMain = getThemeColor("bgMain");
        getContentPane().setBackground(bgMain);
        
        updateThemeUIManager();
        SwingUtilities.updateComponentTreeUI(this);
        
        // Update header components
        titleLabel.setForeground(getThemeColor("textPrimary"));
        
        searchField.setBackground(getThemeColor("inputBackground"));
        searchField.setForeground(getThemeColor("inputText"));
        searchField.setCaretColor(getThemeColor("inputText"));
        searchField.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        searchIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("inputText")));
        trashIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
        settingsIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> getThemeColor("textSecondary")));
        
        // Recreate Search Scope Combo to fix theme issues
        if (searchScopeCombo != null && searchScopeCombo.getParent() != null) {
            Container parent = searchScopeCombo.getParent();
            String selected = (String) searchScopeCombo.getSelectedItem();
            parent.remove(searchScopeCombo);
            
            searchScopeCombo = createStyledComboBox(new String[] { "Current Tab", "All Tabs" }, selected);
            searchScopeCombo.setPreferredSize(new Dimension(120, 35));
            searchScopeCombo.addActionListener(e -> refreshUI());
            
            parent.add(searchScopeCombo, BorderLayout.EAST);
            parent.revalidate();
            parent.repaint();
        } else {
             searchScopeCombo.setBackground(getThemeColor("inputBackground"));
             searchScopeCombo.setForeground(getThemeColor("inputText"));
             searchScopeCombo.setBorder(new EmptyBorder(5, 10, 5, 10));
        }
        
        // Force recreation of all cards to apply new theme colors/fonts
        cardMap.clear();
        contentPanel.removeAll();
        
        // Repaint tabs panel to update highlight color
        if (tabsPanel != null) {
            tabsPanel.repaint();
        }
        
        refreshTabsUI();
        refreshUI();
        
        validate();
        repaint();
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
            preview.setForeground(getThemeColor("cardText"));
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
            Color accent = getThemeColor("accent");

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
                case "Twilight":
                    body = new Color(215, 200, 245);
                    topLeft = new Color(215, 200, 245);
                    bottomRight = new Color(180, 160, 230);
                    break;
                case "Neon Night":
                    body = new Color(15, 15, 15);
                    topLeft = new Color(30, 30, 30);
                    bottomRight = new Color(5, 5, 5);
                    break;
                case "Paper White":
                    body = new Color(255, 255, 255);
                    topLeft = new Color(245, 245, 245);
                    bottomRight = new Color(220, 220, 220);
                    break;
                case "Soft Mint":
                    body = new Color(240, 255, 250);
                    topLeft = new Color(250, 255, 255);
                    bottomRight = new Color(200, 230, 220);
                    break;
                case "Lavender Mist":
                    body = new Color(250, 240, 255);
                    topLeft = new Color(255, 250, 255);
                    bottomRight = new Color(230, 210, 240);
                    break;
                default: // Dark
                    body = new Color(0x222226);
                    topLeft = new Color(0x29292D);
                    bottomRight = new Color(0x1C1C20);
                    break;
            }

            // Main body
            if ("Twilight".equals(theme)) {
                GradientPaint gp = new GradientPaint(0, 0, topLeft, w, h, bottomRight);
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
            } else {
                g2.setColor(body);
                g2.fillRect(0, 0, w, h);
            }

            if (feedbackProgress >= 0) {
                g2.setColor(getThemeColor("cardText"));
                int thickness = (int) (2 + (5 * (1 - feedbackProgress)));
                g2.setStroke(new BasicStroke(thickness));
                int offset = thickness / 2;
                g2.drawRect(offset, offset, w - thickness, h - thickness);
            } else if (hovered) {
                if ("Twilight".equals(theme)) {
                    // Subtle glow effect
                    Color glowColor = new Color(180, 80, 220); // Violet/Magenta
                    for (int i = 0; i < 4; i++) {
                        g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 60 - (i * 10)));
                        g2.setStroke(new BasicStroke(6 - i));
                        g2.drawRect(1, 1, w - 3, h - 3);
                    }
                    g2.setColor(accent);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(1, 1, w - 3, h - 3);
                } else {
                    g2.setColor(accent);
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRect(1, 1, w - 3, h - 3);
                }
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