## FocusGuard v3.0.1

A free, offline study-focus app for Android. Version 3 turns the whole roadmap into features, gives the app a blue look that fades to red while focus is on, and replaces typed numbers with scroll-wheel timers.

> Found a bug? Please report it (see the end) with your phone model and what you tapped.

### Install
1. Download `FocusGuard-v3.0.1.apk` from the Assets below and open it. Android will ask you to allow installs from your browser or file manager.
2. **Uninstall any older FocusGuard first** if Android says it cannot update it (the signing key can change with this release).
3. Open **Blocks** and tap **Fix now** to enable Accessibility. On Android 13+, if the switch is greyed out: Settings > Apps > FocusGuard > three-dot menu > **Allow restricted settings**.
4. Allow **Usage access** from the **Usage** tab. Optional: **Notification access** in **Blocks > Notifications**.
5. Set battery to **Unrestricted** for FocusGuard.

Needs Android 8.0 or newer.

### This update
- Adds the project website and F-Droid store metadata. The app itself behaves the same as v3.0.0, and installs over it.

### What's new in v3
- [x] **Blue to red theme**: blue when idle, fades to red when focus turns on, back to blue after
- [x] **Scroll-wheel timer**: tap the big time on the Focus screen, set hours and minutes with wheels, or pick a preset
- [x] **Notification blocking and management**: silence blocked apps (or everything except allowed apps) during focus, keep a list, digest when focus ends
- [x] **Usage tab**: screen time grouped by category, most used apps, 7-day chart
- [x] **Permanent notification** with today's screen time, top categories and focus state
- [x] **Website blocking** in browsers, plus **study-websites-only** mode
- [x] **Daily limits** per app or category
- [x] **Pause screen** (a breathing wait) before apps you choose
- [x] **Allow-list mode**: block everything except your study apps
- [x] **Stricter strict mode**: closes split screen and picture-in-picture, optional uninstall protection
- [x] **Emergency exit** with a typed confirmation and a cooldown
- [x] **Profiles** (Study, Exam week, Sleep, your own), **Bedtime mode**, **subject tags**
- [x] **Focus score** and **weekly report** with a share button
- [x] **Home-screen widget** and **quick-settings tile**
- [x] **Backup and restore** to a file
- [x] **Hindi** language (Settings > Language)
- [x] **Private release signing key** support (README explains the four GitHub secrets)

### Known limits
- Reels, Shorts, channel and website detection read other apps' screens, so app updates can break them.
- Floating windows cannot be detected (split screen and picture-in-picture are).
- Notification silencing and website blocking depend on the phone and browser.
- English and Hindi only; a few older messages are still English.

### Report a problem
Open an Issue and include: phone model, Android version, the app involved and its version, what you tapped, and what you expected to happen.
