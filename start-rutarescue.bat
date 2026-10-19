@echo off
rem ======================================================================
rem  RutaRescue - start the app on this computer (real Firebase project)
rem
rem  Double-click this file. It:
rem    1. starts the Pixel_7 Android emulator (the phone on the screen)
rem    2. turns on the Android emulator's network (it boots without one)
rem    3. opens RutaRescue and the REAL Firebase console in your browser
rem
rem  The app saves everything in the real Firebase project "rutarescue"
rem  (Authentication + Cloud Firestore). The local Firebase emulators are
rem  no longer used, so nothing here starts them or loads demo data.
rem  Anything already running is reused, so double-clicking again is safe.
rem  To stop: close the Android emulator window.
rem ======================================================================
setlocal
cd /d "%~dp0"

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMULATOR=%SDK%\emulator\emulator.exe"
set "AVD=Pixel_7"
set "CONSOLE=https://console.firebase.google.com/project/rutarescue/firestore/databases/-default-/data"
rem Gradle needs a recent Java; Android Studio ships one.
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

rem ---- 1. Android emulator ----------------------------------------------
"%ADB%" start-server >nul 2>&1
"%ADB%" devices | findstr /b "emulator-" >nul
if %errorlevel%==0 (
    echo [1/3] The Android emulator is already running.
) else (
    echo [1/3] Starting the %AVD% Android emulator...
    start "Android emulator" /min "%EMULATOR%" -avd %AVD% -no-snapshot -no-boot-anim -no-audio -memory 2048 -cores 2
)

rem ---- 2. Wait for Android, then turn on its network --------------------
echo [2/3] Waiting for Android to finish booting...
"%ADB%" wait-for-device
:wait_boot
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr /b "1" >nul
if errorlevel 1 (
    timeout /t 3 /nobreak >nul
    goto wait_boot
)
"%ADB%" shell svc wifi enable
"%ADB%" shell svc data enable

rem ---- 3. Open the app and the real Firebase console ---------------------
echo [3/3] Opening RutaRescue and the Firebase console...
"%ADB%" shell pm list packages com.oop.rutarescue | findstr "com.oop.rutarescue" >nul
if errorlevel 1 (
    echo       RutaRescue is not installed on this emulator yet - installing it...
    call gradlew.bat :app:installDebug --console=plain -q
)
start "" "%CONSOLE%"
"%ADB%" shell monkey -p com.oop.rutarescue -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo.
echo Ready!
echo   Saved data (Cloud Firestore): %CONSOLE%
echo   Family records are in the "families" collection, one document per resident.
echo   Accounts: Firebase console ^> Authentication ^> Users
echo   To stop: close the Android emulator.
echo.
pause
