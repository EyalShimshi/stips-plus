## Stips+ 0.21: your language first, pinned chats, clearer votes

**Language (#32)**
- The intro now starts with **Choose a language**: the phone's language, עברית or English. Picking one switches the app right away.
- You can change it any time in Settings → Appearance → *Language*. On Android 13 and newer it's also in the phone's Settings → Apps → Stips+ → Language.

**Chats (#33)**
- Long-press a chat → *Pin* to keep it on top. Up to five; the latest pin goes first, and a pin shows by its time. Long-press again → *Unpin*.

**Answers**
- **Votes (#35):** the arrow you picked is green (up) or red (down) on a light circle, whatever the score, and it pops as you tap it. The score turns bold once you've voted.
- **Swipes (#34):** *Delete answer* is gone from the left swipe on your own answer. Stips doesn't let you delete your answers, only detach them. If you had picked it, the swipe is back to *Edit*.

**Missing pages (#36)**
- A profile that doesn't exist now says "This user doesn't exist, or their account was deleted", with *Go back*, instead of talking about a question. Chats, an answer's edit history and other pages name what's missing too.

**To test**
1. Settings → About → *Show the intro again*: the language comes first. Pick English, then עברית: the app should switch each time, staying on that step.
2. Settings → Appearance → *Language*: the same three choices.
3. Chats: long-press a chat → *Pin*. Pin five, then try a sixth. Unpin one.
4. Vote up and down on an answer: the arrow you picked should be green or red on a circle, and tapping it again removes the vote.
5. Swipe your own answer to the left: Settings → Questions and answers shouldn't offer *Delete* any more.
6. Send yourself (notes to self) the link https://stips.co.il/profile/999999999 and tap it: the profile should say the user doesn't exist, with *Go back*.

**Heads-up**
- On Android 12 and older, notifications and widgets switch to the new language after the app restarts.
- This release waits for your approval: in the GitHub app, open the run → *Review deployments* → *Approve*.
