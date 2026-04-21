# Stelo → Karoo 2 Direct BLE Setup
 
This guide explains how to set up a direct Bluetooth connection between a Dexcom Stelo CGM sensor and a Hammerhead Karoo 2 cycling computer using a modified xDrip build. No phone is needed during rides.
 
---
 
## What You Need
 
- Hammerhead Karoo 2
- Dexcom Stelo sensor
- Windows PC with ADB installed
- USB cable
- The following files in `C:\Android\platform-tools\` (available in the GitHub repository):
  - `xDrip-working.apk`
  - `working_prefs.xml`
> For ADB installation and APK deployment setup, follow this guide:
> https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html
 
---
 
## Step 1 — Enable Developer Mode on Karoo
 
1. On the Karoo go to **Settings → About**
2. Tap **Build Number** 7 times
3. Go to **Settings → Developer Options**
4. Enable **USB Debugging**
5. Connect the Karoo to your PC via USB
6. Accept the USB debugging prompt on the Karoo screen
---
 
## Step 2 — Verify ADB Connection
 
```cmd
adb devices
```
 
You should see something like `KAROO20XXXXXXX    device`. If it says `unauthorized` check the Karoo screen and tap Allow.
 
---
 
## Step 3 — Install xDrip on Karoo
 
Uninstall any existing xDrip first:
 
```cmd
adb uninstall com.eveningoutpost.dexdrip
```
 
Then install:
 
```cmd
adb install xDrip-working.apk
```
 
---
 
## Step 4 — Push Prefs to Karoo
 
Open xDrip on the Karoo and when prompted allow all permissions. Then go to **Menu → Settings → Transmitter ID** and enter your 4-character Stelo transmitter ID printed on the sensor applicator. Wait 15 seconds for xDrip to fully initialize, then run these commands without closing xDrip:
 
```cmd
adb push working_prefs.xml /data/local/tmp/working_prefs.xml
adb shell run-as com.eveningoutpost.dexdrip cp /data/local/tmp/working_prefs.xml /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml
adb shell run-as com.eveningoutpost.dexdrip chmod 444 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml
adb shell am force-stop com.eveningoutpost.dexdrip
adb shell am start -n com.eveningoutpost.dexdrip/.Home
```
 
> `chmod 444` locks the file as read-only so xDrip cannot overwrite the credentials on restart.
 
---
 
## Step 5 — Connect to Stelo
 
1. In xDrip go to **Menu → System Status → Dex Status**
2. Tap **Restart Collector**
3. Brain State should move from Scanning to Authorizing to Connected
4. First glucose reading arrives within 5 minutes
---
 
## Step 6 — Restore Write Permissions
 
After confirming the connection is working:
 
```cmd
adb shell run-as com.eveningoutpost.dexdrip chmod 644 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml
```
 
---

# ConneXX

ConneXX extends the Hammerhead Karoo sample app with two features: partner metric sharing over Bluetooth Classic, and live blood glucose display from a Dexcom Stelo CGM sensor.

---

## Data Fields

| Field | Description |
|---|---|
| User HR / Power / Speed | Your own sensor data routed through ConneXX |
| Partner HR / Power / Speed | Your partner's live metrics over Bluetooth |
| Partner % of FTP | Partner power as a percentage of their FTP |
| Partner % of Max HR | Partner heart rate as a percentage of their max HR |
| User Glucose | Live blood glucose from Dexcom Stelo with trend arrow |

### Glucose Display

| Display | Meaning |
|---|---|
| `145 →` | Stable |
| `145 ↑` | Rising |
| `145 ↑↑` | Rising rapidly |
| `145 ↓` | Falling |
| `145 ↓↓` | Falling rapidly |
| `---` | No data / sensor disconnected |

### Adding ConneXX Fields to Your Ride Profile

1. On the Karoo go to **Settings → Profiles**
2. Select the profile you ride with and tap **Edit**
3. Tap any data field slot and select **Add Field**
4. Scroll the list to find the ConneXX fields and tap one to add it

> For a visual walkthrough of the profile editor, see the Karoo Support Documentation:
> https://support.hammerhead.io/hc/en-us/articles/4595942669979-Karoo-2-Customising-Profiles

---

## Building & Installing the ConneXX APK

### Step 1 — Enable Developer Mode on Karoo
1. On the Karoo go to **Settings → About**
2. Tap **Build Number** 7 times
3. Go to **Settings → Developer Options** and enable **USB Debugging**
4. Connect the Karoo to your PC via USB and accept the USB debugging prompt on the Karoo screen

### Step 2 — Verify ADB Connection
cd C:\Android\platform-tools
adb devices

You should see `KAROO20XXXXXXX    device`. If it says `unauthorized`, tap Allow on the Karoo screen.

### Step 3 — Build the APK

From Android Studio: Click Run with your Karoo connected — it builds and installs automatically.

From the command line:
./gradlew assembleDebug

APK output path:
sample/build/outputs/apk/debug/sample-debug.apk

Install directly via Gradle:
./gradlew installDebug

Or install manually via ADB:
adb install -r sample/build/outputs/apk/debug/sample-debug.apk

Once installed, ConneXX registers all data fields automatically when a ride starts.

---

## Before & After Every Ride

Before: Force stop xDrip on your phone via Settings → Apps → xDrip → Force Stop.
The Stelo can only bond to one device at a time — the phone must release before the Karoo can connect.

After: Restart xDrip on your phone. It will re-bond with the Stelo automatically on the next advertisement cycle.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `run-as: package not debuggable` | Wrong APK installed — uninstall and reinstall `xDrip-Karoo-prod-debug.apk` |
| `keks_p1 FAILED` during injection | xDrip prefs file not created yet — open xDrip and wait 15 seconds first |
| Missing QR code after injection | Injection didn't take — repeat Step 4 |
| Scanning errors 4+ | Phone xDrip still running — force stop it |
| Mismatch error | Wrong transmitter ID — check the 4-char ID on the sensor applicator |
| Connection drops after first reading | Restore write permissions (Step 6) then restart collector |

---

## How It Works

The Stelo uses a certificate-based BLE authentication protocol internally called KEKS. During initial phone pairing, three certificate parts (keks_p1, keks_p2, keks_p3) are stored in xDrip's preferences. Without these the Karoo shows "Missing QR code" and cannot connect.

The injection script writes these certificates directly into xDrip's preferences file on the Karoo, giving it everything it needs to authenticate with the Stelo.
