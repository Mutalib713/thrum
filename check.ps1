# Thrum's gate. Must pass before any commit that touches code.
#
#   powershell -File C:\Users\USER\MyClaudeProjects\thrum\check.ps1
#
# Runs the QA suite on the PC (seconds, no device) and then a debug build.
# Uses the Gradle wrapper, so it works from a fresh clone with no hand-placed
# Gradle distribution. See CLAUDE.md.

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host "CHECK FAILED: no JDK at $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

$gradlew = Join-Path $root 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Host "CHECK FAILED: no Gradle wrapper at $gradlew" -ForegroundColor Red
    exit 1
}

Write-Host '--- QA suite + debug build ---' -ForegroundColor Cyan
& $gradlew -p $root :app:testDebugUnitTest :app:assembleDebug
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
