# TranslatePlus

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.13+-green.svg)](https://minecraft.net/)
[![Java Version](https://img.shields.io/badge/Java-8+-blue.svg)](https://java.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/3bdoabk/TranslatePlus)

**Real-time chat translation plugin for Minecraft servers using Google Translate API & OpenAI APIs**

TranslatePlus enables players to communicate across language barriers by automatically translating chat messages into their preferred language. Perfect for international servers with diverse player bases.

## ✨ Features
- 🌍 **Real-time Chat Translation** - Messages are automatically translated for players who have it enabled
- 🚀 **100+ Languages Supported** - Powered by Google Translate API
- 🤖 **AI-Powered Translation Option** - Option to use OpenAI GPT models for smarter, context-aware translation
- ⚡ **High Performance** - Async processing with intelligent caching
- 🛡️ **Smart Rate Limiting** - Prevents API abuse and spam
- 🎯 **Blacklist System** - Exclude server commands and technical terms
- 🔧 **Highly Configurable** - Extensive config options for server admins
- 📱 **User-friendly Commands** - Simple `/translate on/off` commands
- 🌐 **RTL Language Support** - Special handling for Arabic, Hebrew, etc.
- 💾 **Memory Efficient** - Built-in caching system reduces API calls
- 🐛 **Debug Mode** - Comprehensive logging for troubleshooting

## 📋 Requirements

- Minecraft Server 1.13 or higher (Spigot/Paper/Bukkit)
- Java 8 or higher
- Google Translate API Key (free tier available)
- Internet connection for translation requests

## 📥 Installation

1. **Download** the latest `TranslatePlus.jar` from the [Releases](https://github.com/AbdelrahmanM1/TranslatePlus/releases) page
2. **Place** the jar file in your server's `plugins/` folder
3. **Start** your server to generate the config files
4. **Get** a Google Translate API key (see [Setup Guide](#-api-key-setup))
5. **Configure** the plugin (see [Configuration](#-configuration))
6. **Restart** your server

## 🔑 API Key Setup

### Getting Your Free Google Translate API Key:

1. **Go to** [Google Cloud Console](https://console.cloud.google.com/)
2. **Create** a new project or select an existing one
3. **Enable** the "Cloud Translation API"
4. **Navigate** to "Credentials" in the sidebar
5. **Click** "Create Credentials" → "API Key"
6. **Copy** your API key
7. **Paste** it in `plugins/TranslatePlus/config.yml`:
   ```yaml
   google-api-key: "YOUR_API_KEY_HERE"
   ```
8. **Restart** your server

> 💡 **Free Tier**: Google provides 500,000 characters per month for free!

## ⚙️ Configuration

### Basic Configuration (`config.yml`):

```yaml
# Your Google Translate API key
google-api-key: "PUT-YOUR-KEY-HERE"

# Default language for new players (ISO 639-1 codes)
default-language: "en"

# Whether to translate commands (experimental)
translate-commands: false

# Debug mode for troubleshooting
debug-mode: false
```

### Advanced Settings:

```yaml
advanced:
  # Maximum cached translations (higher = less API calls, more RAM)
  cache-size: 1000
  
  # API request timeouts (milliseconds)
  connection-timeout: 5000
  read-timeout: 10000
  
  # Message length limits (characters)
  max-message-length: 500
  min-message-length: 2
  
  # Rate limiting (translations per player per minute)
  rate-limit-per-minute: 30
```

### Blacklisted Words:
```yaml
translation-blacklist:
  - "server"
  - "minecraft" 
  - "admin"
  # Add more words that shouldn't be translated
```

## 🎮 Commands

| Command | Description | Permission | Example |
|---------|-------------|------------|---------|
| `/translate on [lang]` | Enable translation | `translateplus.use` | `/tr on es` |
| `/translate off` | Disable translation | `translateplus.use` | `/tr off` |
| `/translate status` | Check your status | `translateplus.use` | `/tr status` |
| `/translate list` | List supported languages | `translateplus.use` | `/tr list` |
| `/translate help` | Show help menu | `translateplus.use` | `/tr help` |
| `/translate reload` | Reload configuration | `translateplus.admin` | `/tr reload` |
| `/translate setdefault <lang>` | Set server default | `translateplus.admin` | `/tr setdefault en` |

### Command Aliases:
- `/tr` - Short alias
- `/trans` - Alternative alias
- `/translation` - Full word alias

## 🔐 Permissions

### Player Permissions:
```yaml
translateplus.use          # Basic translation commands (default: true)
```

### Admin Permissions:
```yaml
translateplus.admin        # Admin commands like reload, setdefault (default: op)
```

## 🌍 Supported Languages

TranslatePlus supports 100+ languages including:

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| `en` | English | `es` | Spanish | `fr` | French |
| `de` | German | `it` | Italian | `pt` | Portuguese |
| `ru` | Russian | `ja` | Japanese | `ko` | Korean |
| `zh` | Chinese | `ar` | Arabic | `hi` | Hindi |
| `th` | Thai | `vi` | Vietnamese | `tr` | Turkish |

Use `/translate list` in-game for the complete list with language names.

## 💡 Usage Examples

### For Players:
```
/translate on es           # Enable Spanish translation
/translate on              # Enable translation with server default language
/translate status          # Check if translation is on/off
/translate off             # Disable translation
```

### For Admins:
```
/translate reload          # Reload plugin configuration  
/translate setdefault es   # Set Spanish as server default
```

### In Chat:
```
Player1: Hello everyone!
Player2 (sees): ¡Hola a todos! [Translated]
```

## 🔧 Troubleshooting

### Common Issues:

**"No API key" error:**
- Make sure your API key is correctly set in `config.yml`
- Verify the API key has no extra spaces or quotes
- Ensure Cloud Translation API is enabled in Google Cloud Console

**"Translation error" in chat:**
- Check your API quota hasn't been exceeded
- Verify your internet connection
- Enable debug mode to see detailed error logs

**Performance issues:**
- Increase `cache-size` to reduce API calls
- Lower `rate-limit-per-minute` to reduce server load
- Consider using Paper for better async performance

**Players not seeing translations:**
- Ensure they have `/translate on` enabled
- Check if messages are being blacklisted
- Verify message length is within limits

### Debug Mode:
Enable debug logging in `config.yml`:
```yaml
debug-mode: true
```

Check server console for detailed logs starting with `[DEBUG]`.

## 📊 Performance

- **Memory Usage**: ~10-50MB depending on cache size
- **CPU Impact**: Minimal (async processing)
- **Network**: Only outbound HTTPS to Google API
- **Storage**: Config files only (~5KB)

## 🤝 Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup:
- Java 8+ JDK
- Maven 3.6+
- Spigot/Paper test server
- IDE with Bukkit API support

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/AbdelrahmanM1/TranslatePlus/issues)


## 📈 Statistics

- **Version**: 1.0.0
- **Languages Supported**: 100+
- **Minimum Java**: 8
- **Tested Servers**: Spigot, Paper, Bukkit

## 🙏 Acknowledgments

- **Google Translate API** - For providing translation services
- **Bukkit/Spigot Community** - For the amazing server platform
- **Contributors** - Thank you to all who help improve this plugin

---

**Made with ❤️ by [3bdoabk](https://github.com/AbdelrahmanM1)**

*If you find this plugin useful, please ⭐ star the repository!*

