# MinimalDPC

A tiny device-policy controller (DPC) app for personal use. Once provisioned as
device owner via ADB, it locks Android's Private DNS to a hardcoded host
(`dcbec8.dns.nextdns.io`) and applies the `DISALLOW_CONFIG_PRIVATE_DNS` user
restriction so the user can't change DNS from the phone's Settings app.

The app has **no launcher activity**, so it doesn't appear in the phone's app
drawer. The only way to open its management UI is via ADB from a PC:

```
adb shell am start -n com.nico.minidpc/.MainActivity
```

That's the lock: phone-only access is impossible without USB cable + adb.

## Set the unlock password (one-time)

The "Lift DNS restriction" and "Clear device owner" buttons in the UI require
a password. Generate one before building:

```
powershell -ExecutionPolicy Bypass -File .\set-password.ps1
```

This prints a random password to the terminal ONCE, writes its SHA-256 hash
to `PasswordHash.kt`, and that's it. Copy the password somewhere safe (give
it to a trusted person, write it on paper, etc.) and close the terminal.
After that, only the hash remains on disk. You do not remember the password.

## Build

1. Open the `MinimalDPC` folder in Android Studio. Let it sync Gradle.
2. Build → Make Project. Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

Or from the command line:
```
gradlew assembleDebug
```

## Install and provision (one-time)

Phone must be factory-reset with no Google account added. USB debugging on.

```
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner com.nico.minidpc/.AdminReceiver
```

Expected: `Success: Device owner set to package ...`

## Apply lockdown

```
adb shell am start -n com.nico.minidpc/.MainActivity
```

In the UI that pops up:
1. Tap **Apply lockdown (DNS + restrict)**. This sets Private DNS to
   `dcbec8.dns.nextdns.io` and enables `DISALLOW_CONFIG_PRIVATE_DNS`.
2. Verify on the phone: Settings → Network → Private DNS should be locked.
3. Verify blocking: open twitch.tv in browser → should fail.

Press home. The activity disappears, and the app does not show in the drawer.

## Undo lockdown (unlock with password)

You need two things:
1. **A PC with ADB and a USB cable** to the phone (USB debugging must be on).
2. **The unlock password** (the one printed by `set-password.ps1` at build
   time and given to a trusted person). Without the password, the unlock
   buttons reject the action.

Steps:

1. Plug phone into PC. Confirm connection:
   ```
   adb devices
   ```
2. Open the hidden management UI:
   ```
   adb shell am start -n com.nico.minidpc/.MainActivity
   ```
3. On the phone, the MinimalDPC UI appears. Type the unlock password into
   the password field.
4. Tap **Lift DNS restriction**. Toast confirms. Private DNS is now
   user-configurable again from Settings.
5. To re-apply the lock later (without needing the password), just tap
   **Apply lockdown** again, or run the open command from step 2 again
   and tap apply.

To fully decommission (allows uninstall):

1. Plug in phone, run the open command from step 2.
2. Type password, tap **Clear device owner**. Toast confirms.
3. From PC: `adb uninstall com.nico.minidpc`
4. Or factory reset.

If you ever lose the password:

- Factory reset the phone is the only way out.
- Or rebuild the APK (from this folder on your PC) with `set-password.ps1`
  to generate a new password, install the new APK over the old one
  (`adb install -r`). This works because the APK is signed with the same
  debug keystore; the device owner status persists across reinstall as
  long as the package name and signature match.

## Notes

- DNS host is hardcoded in `MainActivity.kt`. Change `privateDnsHost` and
  rebuild to point at a different NextDNS config.
- The unlock password is hashed (SHA-256) and baked into the APK. The
  plaintext is never written to disk by `set-password.ps1`.
- Threat model: a motivated user with the source code can rebuild without
  the password gate. The lock is intentional friction, not a real
  cryptographic barrier against the owner of the source.
