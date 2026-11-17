## Stips+ 0.24 (test build): feeds that open at once, any emoji, Undo, and your pins

**Feeds open at once**
- Why your test feed was slow: most tags answer in under half a second, but #גמירות makes Stips fail after about 15 seconds, every time (with the safe filter off), and #גמירה takes about 3.5 seconds. The feed waited for all of them, on every visit.
- Now the phone keeps what every tag brought, the pages you scrolled too (up to 40 MB in the app's cache), and a feed shows it at once. Only the tags that are due are read again, behind it: busy tags every few minutes, quiet ones every few hours, and everything when you pull down.
- A slow tag never holds a feed up. A tag Stips can't read is skipped, named on the feed, and tried again later.
- New and Mixed use the same questions, so switching between them is instant; Hot loads once, then it's kept.
- New questions that Home already loaded come into your feeds without asking Stips again.

**Preloading**
- While the app is open, your feeds are brought up to date in the background with requests to spare: a few seconds after it opens, then every five minutes, and as soon as you change a feed. So opening Feeds usually needs nothing at all.

**Also**
- Live in feeds is gone.
- A feed's emoji can be any emoji: a picker by group, with your recent ones first.
- Pinning or unpinning with a swipe, and turning a question's updates on or off, can be undone from the message (*Undo*, "ביטול").
- A question you pinned shows its pin filled in your accent color: in lists, on Home's hot cards and on its page.

**To test**
1. Open your test feed, leave, and open it again: the second time it shows at once. #גמירות is named as one Stips can't read.
2. Switch New → Mixed → New: no waiting. Hot takes a moment the first time only.
3. Stay on Home for a minute, then open Feeds: it's already there.
4. Edit a feed → the round emoji button → pick any emoji from the tabs; *No emoji* takes it off.
5. Swipe a question to pin it → *Undo*: the pin fills, then empties.
6. In a question, turn updates off → *Undo*.

This test build also has everything from 0.23, which wasn't released to everyone yet:
## Stips+ 0.23 (test build): your feeds, test builds, announcements, the profile photo, and a lot of small things

**Your feeds (#40)**
- A new **Feeds** tab. Stips' categories are a fixed list, but every tag has its own questions: a feed brings up to 15 tags together into one list, **New** (newest first), **Hot** (this week or month, the most answered first) or **Mixed** (one from each tag in turn). It can leave tags out, and show only unanswered questions.
- Swipe between your feeds. With none yet, the tab offers five ready-made ones (love and relationships, the army, driving and cars, money and work, school and exams), or *Create a feed*.
- **Long-press any #tag** in a question (or the button on a tag's page) to add it to a feed, or to start one with it.
- **Settings → Feeds:** your feeds on Home as chips (after *Unanswered*), order, edit, delete, and **share a feed as a link**: on Android 12 and newer it opens straight in Stips+, which offers to add it; without the app, the site shows the feed.
- Feeds live on your phone (backed up with the settings). A feed asks Stips tag by tag, 4 at a time, only while it's on screen, and more as you scroll.

**Test builds and releases (#41)**
- Every change is now a *test build* first. Apps offer it only with Settings → About → **Test builds** on, so the people who installed Stips+ aren't bothered; 0.22 and older never see test builds at all.
- When a test build is good, release it to everyone: GitHub → **Actions** → **Release** → *Run workflow* → its number (empty: the newest). The very same file goes out; every Stips+ offers it within a day, and the website's button points to it.
- 0.22 doesn't offer test builds: install this one from this page, then turn on *Test builds* to get the next ones in the app.

**Announcements (#41)**
- Post one by editing `announcements.json` in the repository (MAINTAINING.md → *Announcements* has a ready example). Every Stips+ from 0.23 reads it every few hours and pops each new one up once; Settings → About → *Last announcement* shows it again.

**Downloads (#42)**
- Settings → About → *For the creator* (your account only): how many times Stips+ was downloaded from GitHub, and how many got the newest release.

**The profile photo works (#46)**
- Changing it always failed: the photo was never read. Now you pick a photo, drag and pinch it inside a circle, and save. The app also checks that Stips really kept it, since Stips' upload says "success" either way.

**Answers (#38, #39)**
- ⋮ → *Copy text* on any answer, and as a swipe you can pick in Settings. Selecting text and *Select all* takes just that answer.
- Your own answers say **You**, anonymous ones too, and quotes of them say so. Anonymous answers (and questions) can now be detached from your account, as on the site; after that they aren't yours: no editing, no "You".

**Pictures (#43)**
- An imgbb link shows only *Show image*; if the picture can't be loaded, the link comes back. The picture viewer has a ⋮: copy the picture, share it, share its link, open it in the browser.

**Home and Settings (#44, #45, #47)**
- Under the hot questions, a **New | Topics** switch in every layout; the layout picks which one Home opens on. "Like the website" is now *חמות, ואז נושאים (כמו באתר)*.
- Settings → Account → *Profile photo and bio* now goes to your profile.
- Sharing a question shares only its link.
- Switching the language to Hebrew and back to the phone's no longer leaves the app left-to-right.

**To test**
1. Feeds tab: add a ready-made feed; switch *New · Hot · Mixed*; scroll down (more loads); ⋮ → *Edit feed* (add a tag from Stips' suggestions, leave one out); ⋮ → *Share*, open the link on the phone: Stips+ offers to add it. Long-press a #tag in a question → add it to a feed. Settings → Feeds → *Show my feeds on Home*: the chip on Home shows the feed.
2. Settings → About: *Test builds*, *Last announcement* ("No announcements yet"), and *For the creator → Downloads* with numbers.
3. Profile → camera → pick a photo → drag and pinch → *Save*. The new photo should show, cut as you chose it.
4. An answer's ⋮ → *Copy text*; one of your anonymous answers: *Detach* is there, and after it the *You* tag is gone.
5. A question with an ibb.co link: only *Show image*; in the viewer, ⋮ → copy, share, share link.
6. Home: switch *New | Topics*; Settings → Account → *Profile photo and bio* opens your profile.
7. Share a question: only the link goes.
8. Settings → Appearance → Language: Hebrew, then *System*; the app stays right-to-left.

**Heads-up**
- Test builds publish by themselves now: no approval step. Only apps with *Test builds* on are offered them.
