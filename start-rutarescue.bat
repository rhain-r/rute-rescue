@echo off
rem ======================================================================
rem  RutaRescue - start everything for local testing (free, no Firebase account)
rem
rem  Double-click this file. It:
rem    1. starts the Firebase Local Emulator Suite (auth, firestore, storage)
rem    2. starts the Pixel_7 Android emulator
rem    3. loads the demo data (only when it started Firebase itself)
rem    4. turns on the Android emulator's network (it boots without one)
rem    5. opens the Firebase Emulator Suite page and the RutaRescue app
rem
rem  Anything already running is reused, so double-clicking again is safe
rem  and does NOT erase your test data.
rem  To stop: close the "Firebase emulators" window (its data is erased)
rem  and close the Android emulator.
rem ======================================================================
setlocal
cd /d "%~dp0"

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMULATOR=%SDK%\emulator\emulator.exe"
set "AVD=Pixel_7"
set "SUITE=http://127.0.0.1:4000/"
rem Gradle needs a recent Java; Android Studio ships one.
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

rem ---- 1. Firebase emulators --------------------------------------------
curl.exe -s -o nul "%SUITE%"
if %errorlevel%==0 (
    echo [1/5] Firebase emulators are already running - keeping their data.
    set "FIREBASE_WAS_RUNNING=1"
) else (
    echo [1/5] Starting the Firebase emulators in a new window...
    start "Firebase emulators" /d "%~dp0firebase" cmd /k npx.cmd --yes firebase-tools@latest emulators:start --project demo-rutarescue --only auth,firestore,storage
)

rem ---- 2. Android emulator ----------------------------------------------
"%ADB%" start-server >nul 2>&1
"%ADB%" devices | findstr /b "emulator-" >nul
if %errorlevel%==0 (
    echo [2/5] The Android emulator is already running.
) else (
    echo [2/5] Starting the %AVD% Android emulator...
    start "Android emulator" /min "%EMULATOR%" -avd %AVD% -no-snapshot -no-boot-anim -no-audio
)

rem ---- 3. Wait for Firebase, then load the demo data ---------------------
echo [3/5] Waiting for Firebase (the first start can take a few minutes)...
:wait_firebase
curl.exe -s -o nul "%SUITE%"
if errorlevel 1 (
    timeout /t 3 /nobreak >nul
    goto wait_firebase
)
if not defined FIREBASE_WAS_RUNNING (
    echo       Loading the demo accounts and advisory...
    call gradlew.bat :tools:emulator:seedEmulator --console=plain -q
)

rem ---- 4. Wait for Android, then turn on its network --------------------
echo [4/5] Waiting for Android to finish booting...
"%ADB%" wait-for-device
:wait_boot
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr /b "1" >nul
if errorlevel 1 (
    timeout /t 3 /nobreak >nul
    goto wait_boot
)
"%ADB%" shell svc wifi enable
"%ADB%" shell svc data enable

rem ---- 5. Open the Emulator Suite page and the app ----------------------
echo [5/5] Opening the Firebase Emulator Suite and RutaRescue...
"%ADB%" shell pm list packages com.oop.rutarescue | findstr "com.oop.rutarescue" >nul
if errorlevel 1 (
    echo       RutaRescue is not installed on this emulator yet - installing it...
    call gradlew.bat :app:installDebug --console=plain -q
)
start "" "%SUITE%"
"%ADB%" shell monkey -p com.oop.rutarescue -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo.
echo Ready!
echo   Firebase Emulator Suite: %SUITE%
echo   Demo accounts: tools\emulator\src\main\java\com\rutarescue\emulator\SeedDemoData.java
echo   To stop: close the "Firebase emulators" window and the Android emulator.
echo.
pause
