run: 44
sha: e1dbe23a27cca71a719fe3edaad6393c8593e7cc
build-and-tests: success
launch-test: success
release: test build dev-44
signing: shared key from the secret
cert-sha256: c0118f1a84a763a7bc13ae6f9c89803c49978745d74bb931b0bb9e16521e352c
apk-bytes: 4025634

## Build errors

## APK check
Checked 14 classes that libraries create by name, in 5336 classes.

Permissions the APK asks for:
- android.permission.INTERNET
- android.permission.ACCESS_NETWORK_STATE
- android.permission.POST_NOTIFICATIONS
- android.permission.RECEIVE_BOOT_COMPLETED
- android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- android.permission.USE_BIOMETRIC
- android.permission.WAKE_LOCK
- android.permission.FOREGROUND_SERVICE
- io.github.eyalshimshi.betterstips.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
No permission to install apps.

## Test failures

## Launch test
### Opened on an emulator (Android 15, API 35; online: yes)

- **earlier-dev-43:** opened, still running after 25s
- **update-over-dev-43:** opened, still running after 25s
- **fresh-install:** opened, still running after 25s
  - shows "Choose a language", as expected
- **second-launch:** opened, still running after 25s
- launcher shortcuts registered: hot, chats
- **shortcut-hot:** opened, still running after 25s
  - shows "Hot questions", as expected
- **shortcut-chats:** opened, still running after 25s
  - shows "Sign in to see your chats", as expected

Result: passed.
