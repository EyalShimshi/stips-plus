## Stips+ 0.23 (test build): test builds, announcements, the profile photo, and a lot of small things

**Test builds and releases (#41)**
- Every change is now a *test build* first. Apps offer it only with Settings → About → **Test builds** on, so the people who installed Stips+ aren't bothered; 0.22 and older never see test builds at all.
- When a test build is good, release it to everyone: GitHub → **Actions** → **Release** → *Run workflow* → its number (empty: the newest). The very same file goes out; every Stips+ offers it within a day, and the website's button points to it.
- This is the first test build: install it from this page, then turn on *Test builds* to get the next ones in the app.

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
1. Settings → About: *Test builds*, *Last announcement* ("No announcements yet"), and *For the creator → Downloads* with numbers.
2. Profile → camera → pick a photo → drag and pinch → *Save*. The new photo should show, cut as you chose it.
3. An answer's ⋮ → *Copy text*; one of your anonymous answers: *Detach* is there, and after it the *You* tag is gone.
4. A question with an ibb.co link: only *Show image*; in the viewer, ⋮ → copy, share, share link.
5. Home: switch *New | Topics*; Settings → Account → *Profile photo and bio* opens your profile.
6. Share a question: only the link goes.
7. Settings → Appearance → Language: Hebrew, then *System*; the app stays right-to-left.

**Heads-up**
- Test builds publish by themselves now: no approval step. Only apps with *Test builds* on are offered them.
