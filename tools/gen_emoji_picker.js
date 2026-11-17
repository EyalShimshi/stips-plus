// Makes app/src/main/assets/emoji_picker.txt, the feed emoji picker's list (v0.24), from Unicode's emoji-test.txt:
// fully-qualified emoji without skin tones, in Unicode's groups (minus "Component"), one line per group.
//   curl -o emoji-test.txt https://unicode.org/Public/emoji/latest/emoji-test.txt
//   node tools/gen_emoji_picker.js emoji-test.txt app/src/main/assets/emoji_picker.txt
// The app shows only those the phone can draw, so a newer list is safe on older phones.
const fs = require("fs");
const src = fs.readFileSync(process.argv[2], "utf8").split(/\r?\n/);
const keys = { "Smileys & Emotion": "smileys", "People & Body": "people", "Animals & Nature": "animals", "Food & Drink": "food",
  "Travel & Places": "travel", "Activities": "activities", "Objects": "objects", "Symbols": "symbols", "Flags": "flags" };
let version = "", date = "", group = null;
const out = new Map();
for (const line of src) {
  if (line.startsWith("# Version:")) version = line.split(":")[1].trim();
  if (line.startsWith("# Date:")) date = line.slice(7).split(",")[0].trim();
  const g = line.match(/^# group: (.+)$/); if (g) { group = keys[g[1]] || null; if (group && !out.has(group)) out.set(group, []); continue; }
  if (!group || line.startsWith("#") || !line.trim()) continue;
  const m = line.match(/^([0-9A-F ]+);\s*fully-qualified\s*#/); if (!m) continue;
  const cps = m[1].trim().split(/\s+/).map(h => parseInt(h, 16));
  if (cps.some(c => c >= 0x1F3FB && c <= 0x1F3FF)) continue; // skin tones
  out.get(group).push(String.fromCodePoint(...cps));
}
let text = `# The feed emoji picker's emoji (v0.24): Unicode's emoji-test.txt, Emoji ${version} (${date}), fully-qualified, without skin tones.\n` +
  `# Copyright (c) Unicode, Inc. Unicode data files: https://www.unicode.org/terms_of_use.html (Unicode License v3).\n` +
  `# One group per line: its key, a tab, then the emoji separated by spaces. Regenerate: MAINTAINING.md, "Feeds".\n`;
let total = 0;
for (const [k, list] of out) { text += k + "\t" + list.join(" ") + "\n"; total += list.length; console.log(k.padEnd(11), list.length); }
fs.writeFileSync(process.argv[3], text, "utf8");
console.log("total", total, "bytes", Buffer.byteLength(text, "utf8"));