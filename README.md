<!-- To point the button at a new release, change the link below (it appears once, in the button). -->
<p align="center">
  <a href="https://github.com/AnmolKumar-dot/FocusGuard/releases/tag/FocusGuard_Android">
    <img src="https://img.shields.io/badge/%E2%AC%87%20Download%20APK-Latest%20release-7FDB4A?style=for-the-badge&logo=android&logoColor=white" alt="Download the latest FocusGuard APK" height="56">
  </a>
</p>

<h1 align="center">FocusGuard</h1>

<p align="center">
  A free, offline focus app for students. Block distracting apps, Instagram Reels and YouTube Shorts,<br>
  and keep YouTube to the study channels you choose.<br>
  No account, no ads, no subscription. Everything stays on your phone.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-alpha-orange" alt="Alpha">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 and newer">
</p>

> **Alpha build.** It is an early test version and has not been tried on many phones. Expect rough edges, and please [report what breaks](#report-a-problem).

---

## Download and install

1. Tap the green **Download APK** button above. On the release page, open **Assets** and download the `.apk` file.
2. Open the file. Android will ask you to allow installs from your browser or file manager. Play Protect may warn about an unknown app, since this does not come from the Play Store.
3. **Had an older FocusGuard installed?** Uninstall it once first. The signing key changed, so the new build cannot install over the old one. Later updates will install over this one.

## First-time setup

1. Open **Blocks** and tap **Fix now**, then turn on **FocusGuard** in Accessibility settings.
2. On Android 13 and newer, if that switch is greyed out: phone Settings > Apps > FocusGuard > three-dot menu > **Allow restricted settings**, then try again.
3. Set battery to **Unrestricted** for FocusGuard (Settings > Apps > FocusGuard > Battery) so Android does not stop it in the background.
4. Choose the apps to block, then start a session from the **Focus** tab.

## Features

| Tab | What you get |
| --- | --- |
| **Focus** | Round timer with **Timer** and **Pomodoro** modes, **strict mode** (no ending early, phone Settings blocked), today's focus time and streak |
| **Planner** | Repeating schedules (for example Mon-Fri 6-8 PM) that turn blocking on by themselves |
| **Blocks** | Instagram **Reels** and YouTube **Shorts** blocking, YouTube **Allow / Study mode / Block**, the blocked-apps list, strict-mode protections |
| **Stats** | Today, streak, total, a 7-day chart and goals |

### YouTube study mode
In **Blocks > Study channels**, type part of a channel's name and pick it from the results. You see its logo, then tap **Add**. During focus, YouTube only plays videos from those channels. Shorts are blocked in this mode.

- Search needs no key or sign-in. It uses an unofficial YouTube address, so it can stop working if YouTube changes it. Typing a channel's exact name always works as a fallback.
- Optional backup: save a free [YouTube Data API key](https://console.cloud.google.com) under **Blocks > Study channels > Backup API key**. Create a project, enable **YouTube Data API v3**, then add an API key with no app restrictions. About 100 searches a day are free.

## Privacy and permissions

Nothing you do in the app is sent anywhere, and there are no accounts, analytics or ads.

| Permission | Why |
| --- | --- |
| **Accessibility** | Sees which app and screen is open during focus, so it can block apps, Reels and Shorts and read the channel name on a YouTube video page. Nothing is recorded or stored. |
| **Internet** | Only for the channel search (your search text goes to YouTube) and loading channel logos. |

## Known limits

- Reels, Shorts and study-channel detection read what Instagram and YouTube show on screen. An app update can break them, and they have only been checked with the English app language.
- Channel search is unofficial and can stop working without warning.
- Strict mode applies to manual sessions. Planner schedules can always be switched off.
- Strict mode blocks phone Settings so FocusGuard cannot be switched off mid-session. If you ever get stuck, wait for the timer to end.
- The alpha is signed with a shared debug key, so treat it as a test build.

## Roadmap

**Next up**
- [ ] Notification blocking and management (silence blocked apps during focus, digest afterwards)
- [ ] App usage grouped by category
- [ ] Permanent notification showing today's usage and focus state

**Planned**
- [ ] Website blocking in browsers and a study-websites-only mode
- [ ] Daily time limits per app or category
- [ ] Pause screen before opening a distracting app
- [ ] Allow-list mode (block everything except chosen study apps)
- [ ] Stricter strict mode (split screen, floating windows, harder to switch off)
- [ ] Profiles (Study, Exam week, Sleep), bedtime mode, subject tags
- [ ] Weekly report and focus score, widget and quick-settings tile
- [ ] Backup and restore, more languages
- [ ] Private release signing key

## Report a problem

Open an [Issue](https://github.com/AnmolKumar-dot/FocusGuard/issues) and include: phone model, Android version, the YouTube and Instagram app versions, what you tapped, and what you expected to happen.

---

## For developers

Built with Kotlin, Android Views and Material 3. Minimum Android 8.0 (API 26).

**Build with GitHub Actions (no PC needed)**
1. Push the project to a GitHub repository so `build.gradle.kts` and `.github` sit at the top level.
2. Open **Actions**, pick **Build APK** and press **Run workflow** (it also runs on every push).
3. Download **FocusGuard-debug-apk** from the run's Artifacts and unzip it.

**Publish a release**
- By hand: **Releases > Draft a new release**, create a tag, paste `RELEASE_NOTES.md`, attach the APK and tick **Set as a pre-release** for alpha or beta builds.
- Automatically: **Actions > Release APK > Run workflow**, enter a tag such as `v0.2.0-alpha`. It builds the APK and publishes the release with `RELEASE_NOTES.md` as the description.

**Fixing detection after an app update**
The Instagram and YouTube screen names the app looks for are in the `companion object` at the bottom of `FocusAccessibilityService.kt`. The no-key search settings are at the top of `YouTubeApi.kt`.
