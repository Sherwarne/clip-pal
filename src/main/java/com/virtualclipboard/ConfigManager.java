package com.virtualclipboard;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private final Properties properties = new Properties();

    public ConfigManager() {
        load();
    }

    private void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Virtual Clipboard Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getBrowser() {
        return properties.getProperty("browser", "System Default");
    }

    public void setBrowser(String browser) {
        properties.setProperty("browser", browser);
    }

    public boolean isIncognito() {
        return Boolean.parseBoolean(properties.getProperty("incognito", "false"));
    }

    public void setIncognito(boolean enabled) {
        properties.setProperty("incognito", String.valueOf(enabled));
    }

    public String getSearchEngine() {
        return properties.getProperty("searchEngine", "Yandex");
    }

    public void setSearchEngine(String engine) {
        properties.setProperty("searchEngine", engine);
    }

    public String getTheme() {
        return properties.getProperty("theme", "Dark");
    }

    public void setTheme(String theme) {
        properties.setProperty("theme", theme);
    }

    public int getFontSize() {
        return Integer.parseInt(properties.getProperty("fontSize", "14"));
    }

    public void setFontSize(int size) {
        properties.setProperty("fontSize", String.valueOf(size));
    }

    public int getMaxHistory() {
        return Integer.parseInt(properties.getProperty("maxHistory", "50"));
    }

    public void setMaxHistory(int max) {
        properties.setProperty("maxHistory", String.valueOf(max));
    }

    public boolean isAutoStart() {
        return Boolean.parseBoolean(properties.getProperty("autoStart", "false"));
    }

    public void setAutoStart(boolean enabled) {
        properties.setProperty("autoStart", String.valueOf(enabled));
    }

    public boolean isAutoSortByDate() {
        return Boolean.parseBoolean(properties.getProperty("autoSortByDate", "true"));
    }

    public void setAutoSortByDate(boolean enabled) {
        properties.setProperty("autoSortByDate", String.valueOf(enabled));
    }
}
