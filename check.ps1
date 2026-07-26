# Thrum's gate. Must pass before any commit that touches code.
#
#   powershell -File C:\Users\mutal\thrum\check.ps1
#
# Runs the QA suite on the PC (seconds, no device) and then a debug build.
# --max-workers=1 is not optional on this machine: Avast locks Gradle transform
# outputs mid-build and a parallel build loses the race. See CLAUDE.md.

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host "CHECK FAILED: no JDK at $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

$gradle = Get-ChildItem 'C:\Users\mutal\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $gradle) {
    Write-Host 'CHECK FAILED: gradle 9.4.1 distribution not found' -ForegroundColor Red
    exit 1
}

Write-Host '--- QA suite + debug build ---' -ForegroundColor Cyan
& $gradle.FullName -p $root :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1
$gradleExit = $LASTEXITCODE

if ($gradleExit -ne 0) {
    Write-Host "CHECK FAILED: gradle exited $gradleExit" -ForegroundColor Red
    exit 1
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    Write-Host "CHECK FAILED: build reported success but no APK at $apk" -ForegroundColor Red
    exit 1
}

$size = [math]::Round((Get-Item $apk).Length / 1MB, 2)
Write-Host ''
Write-Host "CHECK PASSED - tests green, APK $size MB" -ForegroundColor Green
Write-Host $apk
exit 0
