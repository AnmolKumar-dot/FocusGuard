# FocusGuard

A free, offline study-focus app for Android. Everything stays on your phone.

## What is in it
- **Focus**: round timer, Timer or Pomodoro mode, strict mode, daily focus time and streak
- **Planner**: repeating schedules (for example Mon-Fri 6-8 PM) that turn blocking on automatically
- **Blocks**: Shorts and Reels blocking, YouTube Allow / Study mode / Block, blocked apps list, strict-mode protections
- **Stats**: today, streak, total, a 7-day chart and goals
- **Study channels**: search a YouTube channel by name, see its logo, tap Add. No key or sign-in needed.

## Update from the first version
1. Upload this folder's contents to your GitHub repo, replacing the old files (same paths are overwritten).
2. Run the **Build APK** workflow (Actions tab) and download `FocusGuard-debug-apk`.
3. **Uninstall the old FocusGuard once**, then install the new APK. This build is signed with a fixed key, so from the next update on it installs over the top and keeps your settings.
4. Turn the accessibility permission on again if asked.

## Build the APK with GitHub (first time)
1. Create a new GitHub repository and upload the **contents** of this folder, so `build.gradle.kts` and `.github` sit at the top level.
2. Open **Actions**, pick **Build APK**, press **Run workflow** (it also runs on every push).
3. When it finishes, download **FocusGuard-debug-apk** from the run's Artifacts, unzip it and install `app-debug.apk`.

If the build fails, open the failed step and send the error text.

## First-time setup on the phone
1. Open **Blocks** and tap **Fix now** (or phone Settings, Accessibility), then turn on **FocusGuard**.
2. On Android 13 and newer, sideloaded apps can't use this until you open phone Settings > Apps > FocusGuard, tap the three-dot menu and choose **Allow restricted settings**.
3. Set battery to Unrestricted for FocusGuard (Settings > Apps > FocusGuard > Battery) so Android doesn't stop the service.

## Channel search
Blocks > Study channels, then type part of a channel name. Results show the channel logo; tap Add.

- It works with no key. It asks the same YouTube web address the YouTube website uses, the way apps like NewPipe do. This is unofficial, so YouTube can change it and the search may stop working until the app is updated. It has not been tested on a real phone.
- If the search says it is down, you can still add a channel by typing its exact name (button under the list).
- Optional backup: a free YouTube Data API key (Blocks > Study channels > Backup API key). When the no-key search fails, the app tries the official API with it. Get one at https://console.cloud.google.com: create a project, enable **YouTube Data API v3**, then Credentials > Create credentials > API key, with no app restrictions. About 100 searches a day are free.

## Things to know
- Reels, Shorts and study-channel detection read Instagram's and YouTube's on-screen elements. Those can change when the apps update, and this has only been tested by reading the code, not on a real phone. English app language only.
- Study mode compares the channel name YouTube shows on the video page with the exact names you added. If one channel is not recognised, remove it and add it again from the search results.
- Strict mode applies to manual sessions. Schedules can always be switched off in Planner.
- Strict mode blocks phone Settings so FocusGuard can't be switched off mid-session. If you ever get stuck, wait for the timer to end.
- Stopwatch mode and Groups from the reference app are not included.
- If detection stops working after an app update, the values to adjust are in the `companion object` at the bottom of `FocusAccessibilityService.kt`.
