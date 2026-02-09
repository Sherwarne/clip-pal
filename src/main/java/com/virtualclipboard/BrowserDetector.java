package com.virtualclipboard;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowserDetector {
    private static final Map<String, String> BROWSER_PATHS = new HashMap<>();
    private static final Map<String, String> INCOGNITO_FLAGS = new HashMap<>();

    static {
        // Common browser installation paths
        String userHome = System.getProperty("user.home");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        // Chrome
        BROWSER_PATHS.put("Google Chrome", programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
        BROWSER_PATHS.put("Google Chrome (x86)", programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");

        // Edge
        BROWSER_PATHS.put("Microsoft Edge", programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
        BROWSER_PATHS.put("Microsoft Edge (x86)", programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");

        // Firefox
        BROWSER_PATHS.put("Mozilla Firefox", programFiles + "\\Mozilla Firefox\\firefox.exe");
        BROWSER_PATHS.put("Mozilla Firefox (x86)", programFilesX86 + "\\Mozilla Firefox\\firefox.exe");

        // Opera
        BROWSER_PATHS.put("Opera", userHome + "\\AppData\\Local\\Programs\\Opera\\opera.exe");
        BROWSER_PATHS.put("Opera (ProgramFiles)", programFiles + "\\Opera\\opera.exe");

        // Vivaldi
        BROWSER_PATHS.put("Vivaldi", userHome + "\\AppData\\Local\\Vivaldi\\Application\\vivaldi.exe");
        BROWSER_PATHS.put("Vivaldi (ProgramFiles)", programFiles + "\\Vivaldi\\Application\\vivaldi.exe");

        // Incognito flags
        INCOGNITO_FLAGS.put("Google Chrome", "--incognito");
        INCOGNITO_FLAGS.put("Microsoft Edge", "-inprivate");
        INCOGNITO_FLAGS.put("Mozilla Firefox", "-private-window");
        INCOGNITO_FLAGS.put("Opera", "--private");
        INCOGNITO_FLAGS.put("Vivaldi", "--incognito");
    }

    public static List<String> detectInstalledBrowsers() {
        List<String> installed = new ArrayList<>();
        Map<String, String> detected = new HashMap<>();

        // Check each browser path
        for (Map.Entry<String, String> entry : BROWSER_PATHS.entrySet()) {
            File browserFile = new File(entry.getValue());
            if (browserFile.exists()) {
                String browserName = getBrowserDisplayName(entry.getKey());
                if (!detected.containsKey(browserName)) {
                    detected.put(browserName, entry.getValue());
                }
            }
        }

        // Add detected browsers in preferred order
        String[] preferredOrder = {
                "Google Chrome",
                "Microsoft Edge",
                "Mozilla Firefox",
                "Opera",
                "Vivaldi"
        };

        for (String browser : preferredOrder) {
            if (detected.containsKey(browser)) {
                installed.add(browser);
            }
        }

        return installed;
    }

    public static String getBrowserPath(String browserName) {
        // Try to find the actual installed path
        for (Map.Entry<String, String> entry : BROWSER_PATHS.entrySet()) {
            if (getBrowserDisplayName(entry.getKey()).equals(browserName)) {
                File browserFile = new File(entry.getValue());
                if (browserFile.exists()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public static String getIncognitoFlag(String browserName) {
        return INCOGNITO_FLAGS.get(browserName);
    }

    private static String getBrowserDisplayName(String internalName) {
        // Remove architecture suffixes for display
        if (internalName.contains("(x86)") || internalName.contains("(ProgramFiles)")) {
            return internalName.replaceAll(" \\(.*\\)", "");
        }
        return internalName;
    }
}
