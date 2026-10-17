# Calorie Coach (Android)

## Build & install
1. Install Android Studio (Koala or newer).
2. File > Open > select this `CalorieCoach` folder. Let Gradle sync (it downloads Gradle 8.9 and dependencies).
3. Plug in your phone (USB debugging on) or start an emulator, then press Run ▶.
   To get an APK file: Build > Build App Bundle(s)/APK(s) > Build APK(s).

## Get the APK without Android Studio (GitHub builds it for you)
1. Create a free account at github.com, then click **New repository** (any name, e.g. calorie-coach).
2. On the new repo page choose **uploading an existing file**, drag in everything inside this
   `CalorieCoach` folder (including the hidden `.github` folder), and click **Commit changes**.
   (If the web upload skips `.github`, create the file `.github/workflows/build-apk.yml` with
   **Add file > Create new file** and paste its contents.)
3. Open the **Actions** tab. The "Build APK" job runs automatically (about 5–8 minutes).
4. When it shows a green tick, open the run and download **CalorieCoach-apk** under *Artifacts*.
   Unzip it, copy `app-debug.apk` to your phone and open it (allow "Install unknown apps").

## AI photo recognition
- Get an API key at https://console.anthropic.com (Settings > API keys) and add some credit.
- In the app: Profile tab > "AI photo recognition" > paste the key > Save key.
- Take a photo on the Food tab; the app identifies the foods and estimates calories automatically.
  Your note is sent along to help with quantities. Without a key, the note-based estimate is used.

## How it works
- Daily target (expected NET kcal) = BMR (Mifflin–St Jeor) × 1.2 + (target − current kg) × 7700 / 90 days,
  never below 1200 (female) / 1500 (male).
- Activity burn = MET × 3.5 × weight / 200 × minutes.
- Food: with an API key, the photo is analysed by Claude (claude-haiku-5-5). Otherwise calories are estimated from the note using a built-in food table
  (~70 common foods incl. Gulf & South Asian dishes). Quantities like "2 chapati", "150g chicken",
  "half plate biryani" are understood. You can override the number.
- Net = food − activity. Green if within ±10% (min ±150 kcal) of target, red if below or above.
- Data is stored on the phone, one log per day.
