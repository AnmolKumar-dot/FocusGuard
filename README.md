# FocusGuard

A free, offline study-focus app for Android. Everything stays on your phone.

## Features
- Focus sessions with a timer (1 to 720 minutes)
- Block any installed apps while a session runs
- Block Instagram Reels and YouTube Shorts
- YouTube "study channels only" mode
- Strict mode: can't stop early, and Settings is blocked until the timer ends
- Daily focus minutes and streak

## Build the APK with GitHub (no PC needed)
1. Create a new GitHub repository.
2. Upload the **contents** of this folder to it, so `build.gradle.kts` and `.github` sit at the top level of the repo.
3. Open the **Actions** tab, pick **Build APK** and press **Run workflow** (it also runs on every push).
4. When it finishes (a few minutes), open the run and download **FocusGuard-debug-apk** from Artifacts. Unzip it to get `app-debug.apk`.
5. Install the APK on your phone (allow installs from your browser or file manager when asked).

If the build fails, open the failed step in the run and send the error text, and it can be fixed.

## First-time setup on the phone
1. Open FocusGuard and tap **Open accessibility settings**, then turn on **FocusGuard**.
2. On Android 13 and newer, sideloaded apps are blocked from this until you open the phone's Settings > Apps > FocusGuard, tap the three-dot menu and choose **Allow restricted settings**. Then try step 1 again.
3. Turn off battery optimisation for FocusGuard (Settings > Apps > FocusGuard > Battery > Unrestricted) so Android doesn't stop the service.
4. Pick the apps to block, add your study channels, and start a session.

## Things to know
- Reels, Shorts and channel detection read Instagram's and YouTube's on-screen elements. Those names can change when the apps update, and detection has only been written, not tested on a real phone. English app language only.
- Study-channel matching looks for the channel name in the video page, so type names the way YouTube shows them (not case-sensitive, partial names work).
- If detection stops working after an app update, the values to adjust are in the `companion object` at the bottom of `FocusAccessibilityService.kt`.
- Strict mode blocks Settings so the accessibility service can't be switched off mid-session. If you ever get stuck, wait for the timer or restart the phone and the session will still end on time.
- Android may ask you to confirm the accessibility permission again after a phone restart.
