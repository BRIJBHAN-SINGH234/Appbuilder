# Dotsquares Mobile App — Android Template

This is the **Android source template** used by the GitHub Actions cloud build
to generate per-merchant APK / AAB files for the Dotsquares React SPA storefront.

You do **not** edit this template per-merchant. Instead:

1. The Magento module sends merchant config (app name, package ID, colors, icon URL,
   store URL, etc.) to GitHub as `workflow_dispatch` inputs.
2. The workflow patches this template at build time using `sed` + ImageMagick.
3. The build produces a signed APK and AAB, uploaded as a workflow artifact.
4. The Magento admin downloads the artifact and serves it to the user.

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      Single-WebView Hybrid App                           │
│                                                                          │
│  SplashActivity (logo/color from admin)                                  │
│        │                                                                 │
│        ▼  (1.5s splash, then fetches /dsmobileapp/config from store URL)│
│  MainActivity                                                            │
│        │                                                                 │
│        └─ WebView                                                        │
│             ├─ URL: <store_url> (admin-configured)                       │
│             ├─ Custom UA suffix: "DSMobileApp/<version>"                 │
│             ├─ JS bridge: window.DSMobileApp (push tokens, deep links)   │
│             ├─ Pull-to-refresh (admin toggle)                            │
│             └─ Offline fallback page                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

## File Layout

```
android-template/
├── build.gradle                           ← root project config
├── settings.gradle                        ← single-module project
├── gradle.properties                      ← AndroidX, Jetifier flags
├── app/
│   ├── build.gradle                       ← app module — patched by workflow
│   ├── proguard-rules.pro                 ← release minification rules
│   └── src/main/
│       ├── AndroidManifest.xml            ← permissions, activities
│       ├── java/com/dotsquares/mobileapp/
│       │   ├── MainActivity.java          ← WebView host
│       │   ├── SplashActivity.java        ← branded splash
│       │   ├── DSMobileAppBridge.java     ← JS↔Java bridge
│       │   └── ConfigLoader.java          ← fetches /dsmobileapp/config
│       ├── res/
│       │   ├── layout/                    ← activity layouts
│       │   ├── values/
│       │   │   ├── strings.xml            ← patched by workflow
│       │   │   ├── colors.xml             ← patched by workflow
│       │   │   └── styles.xml
│       │   ├── mipmap-*/ic_launcher.png   ← patched by workflow (icon)
│       │   ├── drawable/                  ← splash background, etc.
│       │   └── xml/
│       │       └── network_security_config.xml
│       └── assets/
│           ├── config.json                ← snapshot patched by workflow
│           └── offline.html               ← shown when network down
```

## Patched at Build Time

The workflow uses `sed` to patch these placeholders:

| File                                  | Placeholder              | Replaced With            |
|---------------------------------------|--------------------------|--------------------------|
| `app/build.gradle`                    | `__PACKAGE_ID__`         | `com.merchant.app`       |
| `app/build.gradle`                    | `__VERSION_NAME__`       | `1.0.0`                  |
| `app/build.gradle`                    | `__VERSION_CODE__`       | `42`                     |
| `app/src/main/res/values/strings.xml` | `__APP_NAME__`           | `My Store`               |
| `app/src/main/res/values/strings.xml` | `__STORE_URL__`          | `https://mystore.com/`   |
| `app/src/main/res/values/colors.xml`  | `__THEME_COLOR__`        | `#2c5282`                |
| `app/src/main/res/values/colors.xml`  | `__SPLASH_BG_COLOR__`    | `#ffffff`                |
| `app/src/main/AndroidManifest.xml`    | `__USER_AGENT_SUFFIX__`  | `DSMobileApp/1.0`        |
| `app/src/main/assets/config.json`     | (whole file replaced)    | Decoded from `config_b64`|

App icon files (`mipmap-mdpi`, `mipmap-hdpi`, etc.) are regenerated from the
admin-uploaded icon URL using ImageMagick.

## Manual Build (for testing)

```bash
# In your forked repo
./gradlew assembleRelease bundleRelease
```

Output:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

For local testing, you'll need:
- JDK 17
- Android SDK 34 (set `ANDROID_SDK_ROOT` env var)
- Gradle 8.x (or use the wrapper after `gradle wrapper` once)

## Signing

Keystore is generated **once** by GitHub Actions on the first build, then
stored as a GitHub Secret (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`).

**Important**: Backup these secrets. Losing them means you cannot publish
updates to your Play Store app — Google requires the same signing key
for every update.

See `SETUP-GITHUB.md` (top-level) for first-time setup.
