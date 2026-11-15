## Stips+ 0.22: unanswered questions

**Unanswered questions (#37)**
- A new category on Home, right after *All*: **✨ Unanswered** (ללא תשובות). It shows only the newest questions that no one has answered yet, so you can be the first.
- Stips itself has no such list, so the chip looks a little different from the channels: no channel color, but a sparkle and an outline in Stips+'s colors.
- While it's picked, Home is just that list: no hot questions on top. Live adds new questions as they come, and more load as you scroll.
- Tap *All* or a channel to go back.

**To test**
1. Home → *Unanswered*: every question should show 💬 0, newest first, with no hot cards above.
2. Turn on Live (or wait on the screen): new questions should slide in on top.
3. Scroll down: more should load. If a stretch of new questions all have answers, it says "That's everything for now".
4. Answer one of them, pull to refresh: it should be gone from the list.
5. Settings → Home → *Like the website*, then *Unanswered*: the same list instead of the sections; *All* brings the sections back.

**Heads-up**
- This release waits for your approval: in the GitHub app, open the run → *Review deployments* → *Approve*.
