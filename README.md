<!-- To point the button at a new release, change the link below (it appears once, in the button). -->
<p align="center">
  <a href="https://github.com/AnmolKumar-dot/FocusGuard/releases/tag/FocusGuard_Android">
    <img src="https://img.shields.io/badge/%E2%AC%87%20Download%20APK-Latest%20release-2F8CFF?style=for-the-badge&logo=android&logoColor=white" alt="Download the latest FocusGuard APK" height="56">
  </a>
</p>

<h1 align="center">FocusGuard</h1>

<p align="center">
  A free, offline focus app for students. Block distracting apps, websites, Instagram Reels and YouTube Shorts,<br>
  keep YouTube to your study channels, and see where your screen time goes.<br>
  No account, no ads, no subscription. Everything stays on your phone.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-3.0.0-2F8CFF" alt="Version 3.0.0">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 and newer">
</p>

---

## Download and install

1. Tap the blue **Download APK** button above. On the release page, open **Assets** and download the `.apk` file.
2. Open the file. Android will ask you to allow installs from your browser or file manager. Play Protect may warn about an unknown app, since this does not come from the Play Store.
3. **Had an older FocusGuard (v2 / alpha) installed?** Uninstall it once first if the new one refuses to install, because the signing key changes when you start using your own private key (see [Release signing](#release-signing-your-own-private-key)). After that, updates install over each other.

## First-time setup

1. Open **Blocks** and tap **Fix now**, then turn on **FocusGuard** in Accessibility settings.
2. On Android 13 and newer, if that switch is greyed out: phone Settings > Apps > FocusGuard > three-dot menu > **Allow restricted settings**, then try again.
3. Open **Usage** and allow **Usage access** (for screen time and daily limits).
4. Optional: in **Blocks > Notifications**, allow **Notification access** (to silence notifications during focus).
5. Set battery to **Unrestricted** for FocusGuard (Settings > Apps > FocusGuard > Battery) so Android does not stop it in the background.

## The look

The app is **blue** while you are idle and fades smoothly to **red** when focus mode turns on (a session or a schedule), then back to blue when it ends.

## Features

| Tab | What you get |
| --- | --- |
| **Focus** | Round timer with **Timer** and **Pomodoro** modes. Tap the big time to set it with **scroll wheels** (hours and minutes, plus quick presets). **Strict mode**, **emergency exit**, subject tag, profile, today's focus and streak. Gear icon: **Settings**. |
| **Planner** | Repeating schedules (for example Mon-Fri 6-8 PM), each with its own profile, and **Bedtime mode** that locks everything except the apps you keep open |
| **Usage** | Screen time today, **grouped by category** (Social, Video, Games, Music, Study, News, Other), most used apps, a 7-day chart, and **daily limits** |
| **Blocks** | Profile, Reels / Shorts blocking, YouTube Allow / Study mode / Block, blocked apps, **websites**, **allow-list mode**, **notifications** |
| **Stats** | Today, streak, total, 7-day chart, goals, a **focus score**, a **weekly report** with subject breakdown, and a share button |

Also: a **home-screen widget**, a **quick-settings tile**, a **permanent notification** with today's screen time and focus state, **backup and restore**, and **English and Hindi**.

### Profiles
A profile is a set of blocking rules: blocked apps, allow-list, websites, YouTube mode, notifications. Make one for Study, Exam week or Sleep and switch between them. A session or schedule uses the profile that was selected (or the one the schedule names).

### Websites
In **Blocks > Websites** you can block listed sites, or allow **only study websites**. It reads the address bar of Chrome, Firefox, Samsung Internet, Brave, Edge, Opera and a few more. A browser that is not on that list can still open any site.

### Allow-list mode
Block every app except the ones you choose. The phone app, keyboard, home screen and clock always keep working.

### Notifications
Silence notifications from blocked apps (or everything except allowed apps) during focus. They are kept in a list, and a digest is shown when focus ends. Calls, alarms, navigation and ongoing notifications are never touched.

### Daily limits and the pause screen
Set a daily limit for an app or a whole category; at the limit it is blocked until tomorrow. Choose apps in **Settings > Pause screen** to show a short breathing wait before they open.

### Stricter strict mode
During a strict session, phone Settings is blocked, split screen and picture-in-picture are closed, and an optional **Uninstall protection** (device admin with no powers) makes the app harder to remove on impulse. The **Emergency exit** lets you end a strict session if something real comes up, but you must type a word and then wait a cooldown (24 hours by default) before using it again. You can switch it off in Settings (not during a strict session).

### Focus score
0 to 100 over the last 7 days: 50 points for reaching your weekly goal, 25 for days you focused, 15 for your streak, 10 for how few times you tried to open blocked things.

### YouTube study mode
In **Blocks > Study channels**, type part of a channel's name and pick it from the results. You see its logo, then tap **Add**. During focus, YouTube only plays videos from those channels. Shorts are blocked in this mode.

- Search needs no key or sign-in. It uses an unofficial YouTube address, so it can stop working if YouTube changes it. Typing a channel's exact name always works as a fallback.
- Optional backup: save a free [YouTube Data API key](https://console.cloud.google.com) under **Blocks > Study channels > Backup API key**.

## Privacy and permissions

Nothing you do in the app is sent anywhere, and there are no accounts, analytics or ads. See the full [privacy policy](PRIVACY.md).

| Permission | Why |
| --- | --- |
| **Accessibility** | Sees which app, screen and browser address is open during focus, so it can block apps, sites, Reels and Shorts and read the channel name on a YouTube video page. Nothing is recorded or stored. |
| **Usage access** | Reads how long each app was open, for the Usage tab and daily limits. |
| **Notification access** | Hides notifications during focus and keeps a list. Only if you turn it on. |
| **Post notifications / foreground service** | The permanent usage notification, only if you turn it on. |
| **Device admin (no powers)** | Optional uninstall protection. |
| **Internet** | Only for the channel search and loading channel logos. |

## Disclaimer

FocusGuard is an independent, free project. It is not affiliated with, endorsed by or sponsored by Google, YouTube, Instagram or any other app it can block. Those names belong to their owners. Channel search uses an unofficial YouTube address and Reels / Shorts detection reads on-screen text, so both can stop working when those apps change. The app is provided as is, with no warranty (see the licence). Full details: [Privacy policy](PRIVACY.md).

## Known limits

- Reels, Shorts, channel and website detection read what other apps show on screen. App updates can break them, and they have only been checked with English app languages.
- Channel search is unofficial and can stop working without warning.
- Floating (freeform) windows cannot be detected; split screen and picture-in-picture are.
- Notification silencing and website blocking depend on the phone maker's Android version and the browser.
- Strict mode applies to manual sessions. Schedules can always be switched off.
- Languages: English and Hindi. Some screens that were written earlier (such as the YouTube channel search messages) are still English only.

## Roadmap

Everything from the old list is built. Ideas for next: more languages, per-site time limits, a focus-friends streak share card, app-specific schedules.

## Report a problem

Open an [Issue](https://github.com/AnmolKumar-dot/FocusGuard/issues) and include: phone model, Android version, the YouTube and Instagram app versions, what you tapped, and what you expected to happen.

---

## Licence

[MIT](LICENSE). You are free to use, copy and modify it. Pull requests and issues are welcome.

---

## For developers

Built with Kotlin, Android Views and Material 3. Minimum Android 8.0 (API 26). All user-facing text lives in `tools/strings_src.py`; run `python3 tools/gen_strings.py` to regenerate `res/values/strings.xml` and `res/values-hi/strings.xml`. To add a language, add a column there, list it in `res/xml/locales_config.xml` and the language picker in `SettingsSheet.kt`.

**Build with GitHub Actions (no PC needed)**
1. Push the project to a GitHub repository so `build.gradle.kts` and `.github` sit at the top level.
2. Open **Actions**, pick **Build APK** and press **Run workflow** (it also runs on every push).
3. Download **FocusGuard-debug-apk** from the run's Artifacts and unzip it.

### Release signing (your own private key)

Test builds use a shared debug key. For real releases, make your own key once and store it as GitHub Secrets. Never commit it.

1. Open your repository on GitHub, press `.` or choose **Code > Codespaces > Create codespace** (works in a phone browser). In its terminal run:
   ```
   keytool -genkeypair -v -keystore release.jks -alias focusguard -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks
   ```
   Pick a strong password. Copy the long base64 text. Keep a safe copy of `release.jks` and the password somewhere private (if you lose them you cannot publish updates that install over the old version). Then delete the file from the codespace and the repo.
2. In the repo go to **Settings > Secrets and variables > Actions > New repository secret** and add:
   - `KEYSTORE_BASE64` = the base64 text
   - `KEYSTORE_PASSWORD` = the password
   - `KEY_ALIAS` = `focusguard`
   - `KEY_PASSWORD` = the same password (or the key password you chose)
3. Run **Actions > Release APK** with a tag such as `v3.0.0`. With the secrets present it builds `assembleRelease` signed with your key. Without them it falls back to the debug key and prints a warning.

Switching from the debug key to your private key changes the signature, so everyone must uninstall once and then install the new build. After that updates install over each other.

**Publish a release**
- Automatically: **Actions > Release APK > Run workflow**, enter a tag such as `v3.0.0`. It publishes the release with `RELEASE_NOTES.md` as the description. Leave **pre-release** unticked for an official release.
- By hand: **Releases > Draft a new release**, create a tag, paste `RELEASE_NOTES.md`, attach the APK.

**Fixing detection after an app update**
The Instagram, YouTube and browser address-bar names are in the `companion object` at the bottom of `FocusAccessibilityService.kt`. The no-key search settings are at the top of `YouTubeApi.kt`.
