# Security and privacy in Stips+

Stips+ is an independent Android app for [Stips](https://stips.co.il), not affiliated with it. This page says, in plain words, what the app does with your account and your phone, what it doesn't do, and how to check it. Everything here can be checked in the code; the links point to it.

## How it talks to Stips

- **One address:** `https://stips.co.il/api`, Stips' own JSON endpoint. The site's pages call it, and so does the official app, which shows the site in a WebView. A `name` field picks the call, such as `user.login` or `messages.from_user`. Reads go by GET; writes and signing in go by form POST, so nothing private ends up in a URL. The calls are in [StipsApi.kt](app/src/main/java/io/github/eyalshimshi/betterstips/data/api/StipsApi.kt) and [StipsRepository.kt](app/src/main/java/io/github/eyalshimshi/betterstips/data/StipsRepository.kt), described in [SPEC-04 §2](SPEC-04-data.md).
- **HTTPS only:** the app refuses plain HTTP (`usesCleartextTraffic="false"` in the [manifest](app/src/main/AndroidManifest.xml)). It trusts only Android's built-in certificate authorities, so a certificate installed on the phone to watch its traffic can't read it.
- **It says who it is:** every request carries the User-Agent `StipsPlus/<version> (Android <version>; unofficial; +https://github.com/EyalShimshi/stips-plus)`, so Stips can always see it, slow it down or block it. It also sends the site's Referer, as the site's own pages do.
- **Nothing in between:** requests go from your phone straight to Stips. There's no server of ours, no account of ours, and no ads, analytics or trackers.

## Your password

- You type it on the sign-in screen. It goes once, to `https://stips.co.il/api` (`user.login`, a form POST over HTTPS), and is never written to the phone. Once you're signed in, the sign-in screen clears it ([SignInScreen.kt](app/src/main/java/io/github/eyalshimshi/betterstips/ui/signin/SignInScreen.kt)).
- HTTPS encrypts it on the way. Stips checks the password itself, so anything more the app did to it, such as hashing it first, would only turn the result into the new password.

## What stays on the phone

| What | Where | How it's kept |
|---|---|---|
| The sign-in token (`a_token`) Stips gives back | App-private storage | Since v0.25 it's encrypted (AES-GCM) with a key that Android's keystore makes and keeps, in the phone's secure hardware where it has some. That key never leaves the keystore, so a copy of the app's files alone doesn't hold a token that works ([TokenVault.kt](app/src/main/java/io/github/eyalshimshi/betterstips/data/TokenVault.kt)). It's never in backups or moves to a new phone, and it's deleted when you sign out. If a phone's keystore fails, the token is kept as it was before v0.25, still app-private, and the network log says so |
| The session cookie | Memory only | Sent only to the site that set it; gone when the app closes |
| The chats widget's copy of your latest chats | App-private storage | Only while a chats widget is on the home screen; not in backups; deleted when you sign out |
| Settings, your feeds, saved questions, hidden content | App-private storage | Settings and feeds are in Android's backup, so a reinstall brings them back |
| Questions the feeds read, pictures | The app's cache folder | Public content; Android may clear it any time |
| The network log | Memory only | Which call, its answer code and timing. Never parameters, cookies, tokens, passwords or what you wrote. Nothing leaves the phone unless you share it |
| Crash reports | App-private storage | Only until you copy or report one; nothing is sent by itself |

## Where else it connects

Only `stips.co.il` ever gets your session. Every other connection is made without cookies:

- `cdn.stips.co.il` and `images.unsplash.com`, for question photos and profile pictures.
- `ibb.co`, `i.ibb.co`, `i.imgur.com` and `i.postimg.cc`, only when you open a picture link.
- `media.vocaroo.com` and `media1.vocaroo.com`, only when you press ▶ on a Vocaroo recording.
- `api.github.com` and `raw.githubusercontent.com`, to check for a new version and for announcements.
- `github.com`, only when you tap *Update*.

The [README's privacy section](README.md#privacy) lists when, and how much.

## The privacy screen and the chats lock

Settings → Privacy and safety has these, since v0.25:

- **Hide in recent apps:** the app switcher shows a blank card. Before Android 13, Android can only do this by also blocking screenshots.
- **Block screenshots:** no screenshots or screen recordings of the app.
- **Lock chats:** the Chats tab, every chat and sending to chats open only with your fingerprint, face or screen lock (Android's own prompt). They lock again after the time you choose away from the app. Turning the lock off asks too. While it's on, the chats widget shows names and unread counts without the messages, and from Android 13 chats stay out of recent apps.

The lock guards the screen, not the files: it stops someone holding your unlocked phone, not someone with root. Phone notifications for messages follow Android's lock-screen settings.

## Rooted phones

With root, the memory and files of every app can be read: Stips+'s, the official app's, and any browser's. Stips+ doesn't try to detect root or block debuggers. Root can switch such checks off in minutes, and they'd mostly get in the way of people who rooted their own phones. Obfuscating the code wouldn't hide anything either, since the code is public here. What helps, and what Stips+ does:

- the token is encrypted with a keystore key, so copying the app's files alone doesn't give it away;
- the password is never saved;
- release builds can't be debugged without root.

## Checking an APK

- Every APK is built by GitHub Actions from this repository's public code ([build.yml](.github/workflows/build.yml)). It's unit-tested, checked, and opened on an Android emulator before it's published.
- Since v0.25 each build comes with a signed record of where it was built, from which commit and by which workflow (GitHub's artifact attestations, logged with Sigstore). To check a download, use the [GitHub CLI](https://cli.github.com):

  ```text
  gh attestation verify stipsplus-<number>.apk -R EyalShimshi/stips-plus
  ```

- Every build is signed with the same key. Its certificate's SHA-256 is:

  ```text
  c0118f1a84a763a7bc13ae6f9c89803c49978745d74bb931b0bb9e16521e352c
  ```

  `apksigner verify --print-certs stipsplus-<number>.apk` shows it. Android won't install an update signed with any other key over Stips+.

## How it's made

Stips+ is written with AI assistance (GitHub Copilot, with Claude models). Its maintainer directs and reviews the work. Every change goes through the unit tests, the APK check and the emulator test in CI, and a separate code review, before it's published. The reasoning behind each decision is in [SPEC.md](SPEC.md).

## Reporting a vulnerability

Please report privately, not in a public issue: in this repository, go to **Security → Report a vulnerability** (GitHub's private vulnerability reporting). Say what you found and how to see it. You'll get an answer, and a fix in the next build when it's real.

Problems in Stips itself, such as its API or how it handles passwords, belong to Stips: `contact@stips.co.il`.

## Unofficial, as is

Stips+ isn't made, checked or endorsed by Stips. It's free software under the [GPL-3.0](LICENSE), provided as is, without any warranty: use it at your own risk.

## בעברית, בקצרה

- **לאן זה מדבר:** רק לכתובת `https://stips.co.il/api`, אותה כתובת שהאתר עצמו קורא לה, ותמיד בחיבור מוצפן (HTTPS). אין שרת שלנו באמצע, ואין פרסומות או מעקב.
- **הסיסמה** נשלחת לסטיפס פעם אחת ולא נשמרת. נשמר רק אסימון ההתחברות, מוצפן במפתח של מאגר המפתחות של אנדרואיד, שלא יוצא מהטלפון.
- **בהגדרות, בעמוד פרטיות ובטיחות:** הסתרה באפליקציות האחרונות, חסימת צילומי מסך, ונעילת השיחות בטביעת אצבע, בזיהוי פנים או בנעילת המסך.
- **בטלפון עם רוט** כל אפליקציה חשופה, גם הרשמית וגם הדפדפן. Stips+ לא מעמיד פנים שזה אחרת.
- **כל גרסה** נבנית ב-GitHub מהקוד הפתוח, עם אישור חתום שאפשר לבדוק (`gh attestation verify`), וחתומה תמיד באותו מפתח.
- **מצאת בעיית אבטחה?** כדאי לדווח בפרטיות: Security ← Report a vulnerability במאגר הזה.
- **השימוש באפליקציה** על אחריותך: היא ניתנת כמות שהיא, בלי שום אחריות.
