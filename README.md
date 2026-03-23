<div align="center">
  <img src="docs/images/logo.svg" width="80" height="100" alt="Clip-Pal Logo">
  <h1>🚀 Clip-Pal</h1>
  <p><strong>A sleek, high-performance clipboard manager for Windows built with Java 24.</strong></p>
  <p><em>Experience a modern, animated interface that transforms how you track, organize, and interact with your clipboard history.</em></p>
</div>

---

## ✨ Why Clip-Pal stands out?

Clip-Pal isn't just a list of things you've copied. It's a **smart workspace** designed for visual clarity and local-first performance.

*   **🎨 Premium UI**: Featuring smooth trapezoid-style tabs, animated cards, and a refined "Twilight" theme.
*   **🖼️ Visual First**: Detailed previews for Images, GIFs, and SVGs with aspect ratio calculations and frame counts.
*   **🔍 AI-Powered**: Generate concise captions for copied text using local LLMs (Ollama).
*   **⚡ OCR & Reverse Search**: Extract text from images instantly or find their origins with one-click reverse search across 5+ engines.
*   **📂 Dynamic Organization**: Create up to 10 custom tabs with unique icons and emojis. Drag and drop to reorder your workflow.

---

## 📋 Key Features

| Feature | Description |
| :--- | :--- |
| **Smart Monitoring** | Seamlessly captures Text, URLs, Images, GIFs, and even raw SVG code. |
| **OCR (Optical Character Recognition)** | Turn images into editable text using the built-in Tesseract engine. |
| **Reverse Image Search** | Upload to Catbox.moe and search via Google Lens, Yandex, Bing, TinEye, or SauceNAO. |
| **Local AI Integration** | Connect to [Ollama](https://ollama.com/) for local, private AI-generated summaries. |
| **Advanced Search** | Filter by scope (current tab or global) and sort by date, type, or size. |
| **Configurable Experience** | Choose your browser, toggle incognito, adjust font sizes, and manage history limits. |

---

## 🎨 Visual Guide: Entry Types

Each item in your clipboard is categorized with a sharp SVG icon to help you identify content at a glance:

<table align="center">
  <tr>
    <td align="center"><img src="docs/images/Text.svg" width="40"><br><b>Text</b></td>
    <td>Standard snippets, code blocks, or raw data. Shows character and word counts.</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/URL.svg" width="40"><br><b>URL</b></td>
    <td>Automatically detects links, showing the domain and protocol for quick reference.</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/Image.svg" width="40"><br><b>Image</b></td>
    <td>Visual previews with resolution and aspect ratio details. Supports Reverse Search & OCR.</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/GIF.svg" width="40"><br><b>GIF</b></td>
    <td>Specifically handles animated images, displaying frame count and total duration.</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/SVG.svg" width="40"><br><b>SVG</b></td>
    <td>Renders raw SVG code directly in the UI for instant visual verification.</td>
  </tr>
</table>

---

## 🚀 Getting Started

### Prerequisites
*   **Java Runtime**: Requires **Java 21** or later (optimally Java 24 for full feature support).
*   **Operating System**: Windows (Uses *Segoe UI Variable* and *Roboto* fonts for the best experience).
*   **AI (Optional)**: To use AI Captions, download and run [Ollama](https://ollama.com/download).
    *   *Note: Ollama is purely optional; the app functions perfectly as a clipboard manager without it.*

### Installation
1.  Download the latest release from the [Releases](https://github.com/Sherwarne/clip-pal/releases) page.
2.  Run the `VirtualClipboard.exe` or use the provided `build.bat` to compile from source.

---

## 🚧 Current State & Limitations

Clip-Pal is under active development. While the core experience is stable, please be aware of the following:

### 🐛 Known Limitations
*   **AI for Text Only**: AI-generated captions currently only support text content. Image-to-caption (multimodal) support is not yet implemented.
*   **English-Only OCR**: The built-in Tesseract engine is currently configured for English (`eng`) only.
*   **Internet Dependency**: While the app is local-first, **Reverse Image Search** requires an internet connection to upload images to Catbox.moe.
*   **Windows Focus**: UI and font weights are optimized for Windows; performance or visual fidelity on other platforms is not guaranteed.

### 📈 Future Progress
*   [ ] Multi-language OCR support.
*   [ ] Multi-modal AI support for image descriptions.
*   [ ] Export/Import clipboard collections.

---

## 🤝 Contributing
Contributions are welcome! Feel free to open an issue or submit a pull request if you find a bug or have a feature suggestion.

---

## 🌐 Connect with Me
[GitHub](https://github.com/Sherwarne) | [Project Repository](https://github.com/Sherwarne/clip-pal)

<div align="center">
  <sub>Built with ❤️ by Sherwarne</sub>
</div>
