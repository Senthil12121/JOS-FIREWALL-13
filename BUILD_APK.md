# How to Build & Get the APK File for JOS Firewall

All **67 project files** (Kotlin classes, Room database, XML layouts, drawables, themes, Gradle wrapper, and manifest) are included in the downloadable project ZIP.

---

## Method 1: Android Studio (Build APK in 2 Minutes)

### Step 1: Open the Project
1. Download and extract **JOS-Firewall-Android-Project.zip**.
2. Open **Android Studio** (Koala, Jellyfish, Iguana, Hedgehog, or newer).
3. Select **File > Open** and pick the extracted `JOS-Firewall` folder.
4. Wait for Gradle to finish initial indexing and sync.

### Step 2: Build the APK
1. In the top Android Studio menu bar, click:  
   👉 **Build > Build Bundle(s) / APK(s) > Build APK(s)**
2. In the bottom-right corner, Android Studio will show a notification when finished:  
   **"APK(s) generated successfully for 1 module: Locate"**
3. Click **Locate** to open the folder with your APK:  
   📁 `app/build/outputs/apk/debug/app-debug.apk`

---

## Android Studio Troubleshooting Guide

If Android Studio shows an error during Gradle sync or build:

### 1. Error: "Unsupported class file major version" or Java version error
* **Cause**: Android Gradle Plugin 8.4 requires **Java / JDK 17**.
* **Fix**: In Android Studio, go to:
  * **Windows/Linux**: `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`
  * **macOS**: `Android Studio > Settings (Preferences) > Build, Execution, Deployment > Build Tools > Gradle`
  * Under **Gradle JDK**, select **Embedded JDK 17** (or download Java 17 / 21). Click **Apply**.

### 2. Error: "Failed to find target with hash string 'android-34'"
* **Cause**: Android 14 (API 34) SDK platform is not yet installed on your PC.
* **Fix**: Click **Tools > SDK Manager > SDK Platforms tab**, check **Android 14.0 ("UpsideDownCake") API Level 34**, and click **Apply** to install it.

### 3. Error: "Gradle sync failed"
* **Fix**: Click the **elephant icon** in the top-right toolbar or select **File > Sync Project with Gradle Files**. Make sure your internet connection allows downloading Maven/Google packages.

---

## Method 2: Free Automated Cloud Build via GitHub (No Android Studio Needed!)

If you do not have Android Studio installed on your computer:

1. Create a free repository on [GitHub](https://github.com/new).
2. Push or upload the extracted project files to your GitHub repository.
3. GitHub Actions will automatically detect `.github/workflows/build-apk.yml` and build the APK in the cloud with JDK 17 and Android SDK 34 (~2 minutes).
4. Go to the **Actions** tab on your GitHub repository.
5. Click the latest green workflow run, scroll to **Artifacts**, and download `JOS-Firewall-debug-apk`.

---

## Method 3: Command Line (CLI)

Run inside the project root directory:

* **Linux / macOS:**
  ```bash
  chmod +x gradlew
  ./gradlew assembleDebug
  ```

* **Windows (Command Prompt / PowerShell):**
  ```cmd
  gradlew.bat assembleDebug
  ```

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to phone:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

