# duckAssist

*This project is a fork of [gptAssist](https://github.com/woheller69/gptAssist).*

**duckAssist** is a lightweight, privacy-focused Android WebView wrapper designed specifically for [Duck.ai](https://duck.ai/) (DuckDuckGo's private AI chat service). It bridges the gap between web and native by providing advanced integration features while maintaining the core privacy principles of DuckDuckGo.

Duck.ai is entirely private and secure. It requires no login, allowing you to interact with multiple AI models (GPT-4o, Claude 3, Llama 3, etc.) without compromising your personal identity.

## Key Features

- 🎯 **Focused Interface**: A clean, distraction-free environment tailored for AI chat, complete with a native progress bar for page loading feedback.
- 🚀 **"Ask duck.ai" Integration**: Select text in any application on your device and choose "Ask duck.ai" from the context menu. The app will automatically launch and paste your query directly into the chat.
- 📑 **Multi-Window Support**: Every "Ask" trigger opens in a new Android task. This allows you to maintain multiple independent AI conversations and switch between them seamlessly via the Recents menu.
- 🌐 **Smart Link Handling**: Navigation is optimized for the AI experience. All external links clicked within the app are automatically redirected to your system's default web browser, keeping your chat session secure and focused.
- 🛡️ **Enhanced Privacy**:
    - **Session Persistence**: User data and cookies are preserved so you don't lose your settings.
    - **Automated Cache Management**: The application cache is purged whenever you leave or close the app to protect your privacy and save device storage.
- 📁 **Native File Support**: Seamlessly upload documents for AI analysis and download attachments directly to your device's Downloads folder.

## Usage

### Ask duck.ai
1. Highlight any text in your browser, email, or messaging app.
2. Tap the three dots (overflow menu) and select **Ask duck.ai**.
3. The app will open a new window and automatically paste your text into the chat prompt.

### External Navigation
Simply click on any link or citation provided by the AI. The app will instantly launch your preferred mobile browser to view the content, leaving your AI conversation perfectly preserved in duckAssist.

## screenshots

<p align="center">
  <img src="screenshot/image (1).jpg" width="30%" />
  <img src="screenshot/image (2).jpg" width="30%" />
  <img src="screenshot/image (3).jpg" width="30%" />
</p>

## License

This application is licensed under the GPLv3.

The app uses:
- Parts from GMaps WV (https://gitlab.com/divested-mobile/maps) which is licensed under the GPLv3.

## Contributing

If you encounter any issues or have suggestions for improvements, please open an issue in the repository. Pull requests are always welcome.
