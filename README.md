# MiniiChat

A small, open-source Android chat client for any OpenAI-compatible LLM API. It is built with Kotlin and Jetpack Compose, has no backend, and uses keys supplied by the user.

## Wear OS and Android watch support

MiniiChat includes a dedicated layout for round Android watches. It has been tested on the HUAWEI GLL-AL09 running Android 10 at 466 × 466 px. The debug build uses the separate `com.miniichat.debug` package, so it can be installed alongside the regular app.

- An Android foreground service keeps a chat request active when the screen turns off; ongoing output is saved periodically and restored on return. A force stop, system process kill, lost connection or supplier timeout can still interrupt generation.
- Swipe inward from the left or right rim to open history or provider/model controls. Messages use larger, selectable text and a readable fallback for common TeX commands such as `\\sqrt`.
- The crown scrolls long messages, conversation lists, settings and provider lists.
- Answers support headings, lists, tables, code blocks, copy actions and common mathematical notation.
- Images are sent to compatible vision models. Text, Markdown, CSV, JSON, and XML files up to 256 KiB are included as text; PDF and other binary files require a separate extraction implementation.
- Optional web search uses a separately supplied Brave Search API key and sends result summaries with URLs to the chosen model. Enable it in settings; the model provider's key does not grant Brave Search access.
- Provider API keys and the search key are encrypted at rest with Android Keystore. Android backup is disabled so a restored database cannot refer to a missing encryption key.

## Features

- **Multi-provider** — point at any OpenAI-compatible endpoint (OpenAI, OpenRouter, DeepSeek, Groq, Mistral, Together, Gemini OpenAI shim, SiliconFlow, Ollama, LM Studio, custom). 11 built-in presets.
- **Auto-fetch models** — `GET /models` pulls the model list per provider; manual add and remove also supported. Lists collapse after 5 entries.
- **Bottom-sheet model picker** — tap the chip in the top bar, search across all providers.
- **Custom assistants** — name, emoji avatar, system prompt, optional temperature override. 4 built-in presets (Default / Coder / Translator / Writer).
- **Prompt variables** — `{model} {provider} {assistant} {date} {time} {datetime} {weekday} {locale}` rendered into the system prompt.
- **Per-provider customization** — custom HTTP headers and extra body params (data layer; visual editor coming).
- **Media attachments** — pick image or file from `+` menu. Images are sent as base64 data URLs (works with vision-capable models).
- **Streaming** — Server-Sent-Events with a stop button.
- **Markdown and formulas** — headings, tables, fenced code blocks, lists, quotes, bold / italic / inline code / strikethrough, and readable fallbacks for common TeX notation.
- **Material You + theme mode** — dynamic color on Android 12+, plus follow-system / light / dark.
- **Bilingual** — English and 简体中文 (Settings → Language).
- **Pure local storage** — DataStore preferences, no analytics, no backend.

## Download

- **Latest release**: <https://github.com/Minis233/miniichat/releases/latest>
- Each release ships a signed `release` APK (for general use) and a `debug` APK (for development).

## Configuration

1. Open the drawer (top-left) → **Settings → Providers**
2. **Add provider**, pick a preset (or "Custom"), paste your API key
3. Tap **Fetch models**, or add a model id manually
4. Back to chat, tap the model chip in the top bar to switch model
5. (Optional) **Settings → Assistants** to set up role-specific system prompts

Other compatible endpoints:

| Provider | Base URL | Example model |
| --- | --- | --- |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| OpenRouter | `https://openrouter.ai/api/v1` | `openrouter/auto` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.1-70b-versatile` |
| Mistral | `https://api.mistral.ai/v1` | `mistral-small-latest` |
| Together | `https://api.together.xyz/v1` | `meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo` |
| Gemini (OpenAI shim) | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-1.5-flash` |
| SiliconFlow | `https://api.siliconflow.cn/v1` | `Qwen/Qwen2.5-7B-Instruct` |
| Ollama | `http://<host>:11434/v1` | `llama3.2` |
| LM Studio | `http://<host>:1234/v1` | (your loaded model) |

> The base URL field auto-appends `/v1` if you forget it. For Ollama / LM Studio on a real device, replace `10.0.2.2` (emulator-only) with your computer's LAN IP.

## Build

The GitHub Actions workflow builds debug and release APKs on every push. Tagged builds also create a GitHub Release.

```bash
# Local build (needs JDK 17 + Android SDK)
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
# APKs at app/build/outputs/apk/{debug,release}/
```

Without a configured release keystore, release variants are debug-signed for local testing. For a production release, provide these environment variables or Gradle properties:

```text
RELEASE_STORE_FILE=/absolute/path/to/release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

For tagged GitHub Releases, configure these repository secrets:

```text
RELEASE_KEYSTORE_BASE64   # base64 of the keystore file
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Never commit a keystore or its passwords. A release APK signed with a different key cannot update an app signed with the old key, so choose and protect the production key before distributing installable builds.

Earlier repository history tracked a development keystore. Treat that key as public and generate a new production key before the next official release.

## Project layout

```
app/src/main/kotlin/com/miniichat
├── MainActivity.kt                  # Compose entry, locale + theme
├── ChatViewModel.kt                 # State + send/stream/persist
├── api/LlmClient.kt                 # ktor + SSE, multipart content
├── data/                            # DataStore (settings + conversations + providers + assistants)
├── ui/AppRoot.kt                    # Routing, drawer, back-stack
├── ui/ChatScreen.kt                 # Top bar, message stream, bubbles
├── ui/InputBar.kt                   # Text input + attachment picker
├── ui/ModelPicker.kt                # Bottom-sheet model selector
├── ui/Drawer.kt                     # Date-grouped chat list, search
├── ui/SettingsScreen.kt             # Settings hub
├── ui/ProvidersScreen.kt            # Provider editor + fetch models
├── ui/AssistantsScreen.kt           # Assistant editor
├── ui/MarkdownText.kt               # Inline markdown renderer
└── ui/theme/Theme.kt                # Material 3 + dynamic color
```

## License

[MIT](LICENSE)

## Acknowledgements

This project's feature scope is inspired by two excellent open-source LLM clients. **No source code from either project is copied** — MiniiChat is original Kotlin/Compose, and the references below are to their public README feature lists (functionality is not protected by copyright). Both deserve a star.

- **[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)** — A native Android LLM chat client, Apache-2.0. Inspired the Material You theming, multi-provider switching, prompt-variables format, and custom-headers idea.
- **[Chevey339/kelivo](https://github.com/Chevey339/kelivo)** — A Flutter LLM chat client, AGPL-3.0. Inspired the assistant-presets concept, multi-language support, and the per-provider extra-body customization angle.

If you build something on top of MiniiChat, please go give those projects a ⭐ as well.
