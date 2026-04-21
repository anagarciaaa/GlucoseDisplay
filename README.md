# Stelo → Karoo 2 Direct BLE Setup

This guide explains how to set up a direct Bluetooth connection between a Dexcom Stelo CGM sensor and a Hammerhead Karoo 2 cycling computer using a modified xDrip build. No phone is needed during rides.

---

## What You Need

- Hammerhead Karoo 2
- Dexcom Stelo sensor
- Windows PC with ADB installed
- USB cable
- The following files in `C:\Android\platform-tools\`:
  - `xDrip-Karoo-prod-debug.apk`
  - `inject_prefs_full.sh`

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
cd C:\Android\platform-tools
adb devices
```

You should see something like `KAROO20XXXXXXX    device`. If it says `unauthorized` check the Karoo screen and tap Allow.

---

## Step 3 — Install Modified xDrip

Uninstall any existing xDrip first:

```cmd
adb uninstall com.eveningoutpost.dexdrip
```

Then install the debug build:

```cmd
adb install xDrip-Karoo-prod-debug.apk
```

---

## Step 4 — Inject Credentials

This replaces the QR code scan step in the standard xDrip G7/Stelo setup guide.

First open xDrip on the Karoo and wait 15 seconds for it to fully initialize. Then run these commands:

**Push and enable the injection script:**
```cmd
adb push inject_prefs_full.sh /data/local/tmp/inject_prefs_full.sh
adb shell chmod 755 /data/local/tmp/inject_prefs_full.sh
```

> `chmod 755` makes the script executable so the shell can run it.

**Run the injection while xDrip is open:**
```cmd
adb shell run-as com.eveningoutpost.dexdrip sh /data/local/tmp/inject_prefs_full.sh
```

You should see:
```
keks_p1 OK
keks_p2 OK
keks_p3 OK
dex_txid OK
collection_method OK
use_ob1 OK
Done!
```

If anything shows FAILED wait 10 seconds and run the injection command again.

**Lock the prefs file so xDrip cannot overwrite it on restart:**
```cmd
adb shell run-as com.eveningoutpost.dexdrip chmod 444 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml
```

**Restart xDrip:**
```cmd
adb shell am force-stop com.eveningoutpost.dexdrip
adb shell am start -n com.eveningoutpost.dexdrip/.Home
```

---

## Step 5 — Configure xDrip on Karoo

1. In xDrip tap **Menu → Settings → Transmitter ID**
2. Enter your 4-character Stelo transmitter ID (printed on the sensor applicator)
3. Go to **Menu → System Status → Dex Status**
4. Tap **Restart Collector**

Brain State should move from Scanning to Authorizing to Connected. First glucose reading arrives within 5 minutes.

---

## Step 6 — Restore Write Permissions

After confirming the connection is working, restore normal file permissions:

```cmd
adb shell run-as com.eveningoutpost.dexdrip chmod 644 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml
```

---

## Before Every Ride

Force stop xDrip on your phone via **Settings → Apps → xDrip → Force Stop**. The Stelo can only bond to one device at a time so the phone must release the connection before the Karoo can take over.

## After Every Ride

Restart xDrip on your phone. It will re-bond with the Stelo automatically on the next advertisement cycle.

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

The Stelo uses a certificate-based BLE authentication protocol internally called KEKS. During initial phone pairing, three certificate parts (`keks_p1`, `keks_p2`, `keks_p3`) are stored in xDrip's preferences. Without these the Karoo shows "Missing QR code" and cannot connect.

The injection script writes these certificates directly into xDrip's preferences file on the Karoo, giving it everything it needs to authenticate with the Stelo over BLE without a phone.

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

## Stelo → Karoo 2 Direct BLE Setup

Direct Bluetooth connection between a Dexcom Stelo CGM sensor and a Hammerhead Karoo 2 using a modified xDrip build. No phone is needed during rides.

### What You Need
- Hammerhead Karoo 2
- Dexcom Stelo sensor
- Windows PC with ADB installed
- USB cable
- The following files in C:\Android\platform-tools\:
  - xDrip-Karoo-prod-debug.apk
  - inject_prefs_full.sh

### Step 1 — Enable Developer Mode on Karoo
1. On the Karoo go to Settings → About
2. Tap Build Number 7 times
3. Go to Settings → Developer Options
4. Enable USB Debugging
5. Connect the Karoo to your PC via USB
6. Accept the USB debugging prompt on the Karoo screen

### Step 2 — Verify ADB Connection
cd C:\Android\platform-tools
adb devices

You should see something like `KAROO20XXXXXXX    device`. If it says `unauthorized`, tap Allow.

### Step 3 — Install Modified xDrip
adb uninstall com.eveningoutpost.dexdrip
adb install xDrip-Karoo-prod-debug.apk

### Step 4 — Inject Credentials
Open xDrip on the Karoo and wait 15 seconds, then run:

adb push inject_prefs_full.sh /data/local/tmp/inject_prefs_full.sh
adb shell chmod 755 /data/local/tmp/inject_prefs_full.sh
adb shell run-as com.eveningoutpost.dexdrip sh /data/local/tmp/inject_prefs_full.sh

Expected output:
keks_p1 OK
keks_p2 OK
keks_p3 OK
dex_txid OK
collection_method OK
use_ob1 OK
Done!

If anything shows FAILED, wait 10 seconds and run the injection again.

Lock the prefs file:
adb shell run-as com.eveningoutpost.dexdrip chmod 444 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml

Restart xDrip:
adb shell am force-stop com.eveningoutpost.dexdrip
adb shell am start -n com.eveningoutpost.dexdrip/.Home

### Step 5 — Configure xDrip on Karoo
1. Tap Menu → Settings → Transmitter ID
2. Enter your 4-character Stelo transmitter ID (printed on the sensor applicator)
3. Go to Menu → System Status → Dex Status
4. Tap Restart Collector

Brain State should move: Scanning → Authorizing → Connected
First glucose reading arrives within 5 minutes.

### Step 6 — Restore Write Permissions
adb shell run-as com.eveningoutpost.dexdrip chmod 644 /data/data/com.eveningoutpost.dexdrip/shared_prefs/com.eveningoutpost.dexdrip_preferences.xml

---

## Before & After Every Ride

Before: Force stop xDrip on your phone via Settings → Apps → xDrip → Force Stop.
The Stelo can only bond to one device at a time — the phone must release before the Karoo can connect.

After: Restart xDrip on your phone. It will re-bond with the Stelo automatically on the next advertisement cycle.

---

## Troubleshooting

run-as: package not debuggable
  → Wrong APK installed — uninstall and reinstall xDrip-Karoo-prod-debug.apk

keks_p1 FAILED during injection
  → xDrip prefs file not created yet — open xDrip and wait 15 seconds first

Missing QR code after injection
  → Injection didn't take — repeat Step 4

Scanning errors 4+
  → Phone xDrip still running — force stop it

Mismatch error
  → Wrong transmitter ID — check the 4-char ID on the sensor applicator

Connection drops after first reading
  → Restore write permissions (Step 6) then restart collector

---

## How It Works

The Stelo uses a certificate-based BLE authentication protocol internally called KEKS. During initial phone pairing, three certificate parts (keks_p1, keks_p2, keks_p3) are stored in xDrip's preferences. Without these the Karoo shows "Missing QR code" and cannot connect.

The injection script writes these certificates directly into xDrip's preferences file on the Karoo, giving it everything it needs to authenticate with the Stelo.
