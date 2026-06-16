# Organ Clock — TCM widget

A minimal Android home-screen widget. It shows the organ that is most active
*right now* according to the Traditional Chinese Medicine "organ clock" (large
text), with a few supporting herbs beneath (small text). The display flips
automatically every 2 hours.

There's a small settings screen to pick the display language and choose which
organs should notify you when they become active.

## Settings

Open settings by **tapping the widget**, or from the **Organ Clock** icon in the
app drawer. Two sections:

- **Language** — *System default*, *English*, or *Čeština*. This overrides the
  phone language for this app only; the widget and notifications both follow it.
- **Notify me when active** — tick any organs you want a notification for. When a
  ticked organ becomes active (on its 2-hour boundary), you get one notification
  (organ name + herbs). The first time you enable one, Android asks for
  notification permission — allow it, or notifications stay silent.

Notifications keep working even without the widget on screen (they survive
reboots), as long as you've placed the widget or opened settings at least once.

## The schedule

| Window        | Organ           | Emotion |
|---------------|-----------------|---------|
| 23:00 – 01:00 | Gallbladder     | Anger   |
| 01:00 – 03:00 | Liver           | Anger   |
| 03:00 – 05:00 | Lung            | Grief   |
| 05:00 – 07:00 | Large Intestine | Grief   |
| 07:00 – 09:00 | Stomach         | Worry   |
| 09:00 – 11:00 | Spleen          | Worry   |
| 11:00 – 13:00 | Heart           | Joy     |
| 13:00 – 15:00 | Small Intestine | Joy     |
| 15:00 – 17:00 | Bladder         | Fear    |
| 17:00 – 19:00 | Kidney          | Fear    |
| 19:00 – 21:00 | Pericardium     | Joy     |
| 21:00 – 23:00 | Triple Burner   | Joy     |

The emotion follows the organ's five-element (Wood→Anger, Fire→Joy, Earth→Worry,
Metal→Grief, Water→Fear), shown on the small subtitle line beside the time window.

## Editing the text / translations

All organ names, time windows, and herb lists live in string-resource files —
**not** in the Java code. The widget automatically shows the language your phone
is set to (Czech if the phone is in Czech, English otherwise).

- English (default): `app/src/main/res/values/strings.xml`
- Czech: `app/src/main/res/values-cs/strings.xml`

Each file has four lists — `windows`, `organs`, `emotions`, `herbs` — with 12
`<item>`s, one per 2-hour slot, in the order shown in the table above
(Gallbladder first). Edit
the `<item>` text, push, rebuild, reinstall. Czech only overrides `organs` and
`herbs`; the time `windows` fall back to the default file. To add another
language, copy `values/` to `values-<code>/` (e.g. `values-de/`) and translate.

## Build the APK in the cloud (no tools on your PC)

1. Create a free repo on GitHub and push this folder to it:
   ```
   git init
   git add .
   git commit -m "Organ Clock widget"
   git branch -M main
   git remote add origin https://github.com/<you>/organclock.git
   git push -u origin main
   ```
2. On GitHub, open the **Actions** tab. The **Build APK** workflow runs
   automatically on push (takes ~2 min). If it doesn't start, click it and press
   **Run workflow**.
3. When it finishes, open the run and download the **organ-clock-apk** artifact
   (a `.zip`). Inside is `app-debug.apk`.

## Install on the Pixel 9 / GrapheneOS

Easiest, no cable needed:

1. Download the artifact `.zip` directly on the phone, or transfer the
   `app-debug.apk` to it.
2. Unzip if needed, then tap `app-debug.apk`. GrapheneOS will ask to allow
   installing from this source — allow it, then **Install**.
3. Long-press the home screen → **Widgets** → find **Organ Clock** → drag it out.

This is a *debug-signed* APK, which is fine for personal use. Updates: bump
`versionCode` in `app/build.gradle`, push, rebuild, and install over the top.

## Notes

- Minimum Android 8 (API 26). Targets the latest Android, works on Android 16.
- The widget refreshes on a 2-hour boundary via a doze-friendly alarm, with a
  30-minute periodic refresh as a safety net (so no exact-alarm permission is
  needed). Worst-case staleness after a reboot is a few minutes.
- Tap the widget to force an immediate refresh.
