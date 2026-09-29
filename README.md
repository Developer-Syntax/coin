# RollerCoin Android

Native Android application and automation dashboard for RollerCoin, built with Kotlin and Jetpack Compose (Material Design 3).

## Features

- **Dashboard**:
  - Live mining hashpower tracking (Total Power, Penalty, Net Power in Gh/s, Th/s, Ph/s, Eh/s, Zh/s)
  - Account Profile status (User ID, Active/Banned status, Miners count, League, Registration date, Public profile URL)
  - PC Miner configuration (PC Name, Current Level / Max Level, Games to next level, Power holding days)
  - Multi-currency balances and configs
- **15 Mini-Games Catalog**:
  - Complete games catalog: Coinclick, Token Blaster, Flappy Rocket, Cryptonoid, Coin-match, Crypto Hamster, 2048 Coins, Hash Rush, Dr.Hamster, Token Surfer: Snow Ride, CryptoMania, Hamster Climber, Coin Fisher, Mission Hamspossible, Crypto Hex
  - Level-based dynamic reward calculation using the official RollerCoin table
  - Exact game countdown duration timers
  - Manual "Play Now" launcher with live countdown progress overlay
- **Auto-Play Bot Runner**:
  - Automated loop that scans all 15 mini-games for cooldown availability
  - Sequentially plays available games using start/finish encryption tokens
  - Smart cooldown detection and waiting timer when all games are cooling down
  - Configurable delay between games (3 to 30 seconds)
  - Session statistics: Total games played, Cycle count, Round power, Total session mined power
  - Live activity terminal / logs viewer with color-coded events
- **Authentication & Security**:
  - Email OTP authentication flow with CAPTCHA verification
  - Alternative Direct Bearer Token entry
  - AES-256-CBC game token encryption with UID-derived MD5 key
  - Keystore-backed secure token storage (`TokenStore`)
- **Jetpack Compose UI**:
  - Futuristic dark crypto gaming aesthetic (Navy `#0B1020`, Neon Amber `#F59E0B`, Neon Cyan `#06B6D4`, Emerald `#10B981`)
  - Material 3 navigation bar and adaptive layout
  - Edge-to-edge support with status and navigation bar insets
  - Custom adaptive launcher icon

## Architecture

- `com.rollercoin.mobile.ui.screens`: `DashboardScreen`, `GamesScreen`, `BotRunnerScreen`, `AccountScreen`
- `com.rollercoin.mobile.ui.components`: `GameItemCard`, `StatTile`, `ActiveGameOverlayBanner`, `LogItemView`
- `com.rollercoin.mobile.ui.theme`: Centralized M3 `RollerCoinTheme`, `Color.kt`
- `RollerCoinViewModel`: Coroutine-based MVVM state flows for auth, dashboard, live games, and bot automation
- `RollerCoinRepository`: REST API client & OkHttp WebSocket integration
- `Crypto`: AES-256-CBC encryption matching the RollerCoin protocol
- `TokenStore`: Encrypted SharedPreferences with Android Keystore

## CI/CD Build via GitHub Actions

This repository includes an automated GitHub Actions workflow (`.github/workflows/build.yml`) that builds the APK:

- **Automatic Trigger**: Triggers automatically on every `push` and `pull_request` to `main` / `master`.
- **Manual Trigger (`workflow_dispatch`)**: Can be triggered manually from the GitHub Actions tab with choice of `debug` or `release` build.
- **Artifacts**: Once the build completes, the output APK is uploaded as a downloadable artifact in the run summary (`RollerCoin-debug-apk` / `RollerCoin-release-apk`).
- **Resilience**: Automatically ensures required Android SDK components (API 36 & build-tools 36.0.0) and debug keystore are present to prevent any build failures.
