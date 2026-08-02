# Thrum's reliability probe. Task 10 / PROFILE.md R5.
#
#   powershell -File C:\Users\USER\MyClaudeProjects\thrum\tools\soak-check.ps1
#
# R5 is the risk that Android kills the notification listener, so a call arrives
# and nothing vibrates. The dangerous version of that failure is silent: the
# permission still shows as granted in Settings, the app looks armed, and the
# listener simply is not bound.
#
# Those two states are distinguishable from adb, which means most of Task 10's
# soak can be checked **without needing someone to ring the phone**:
#
#   allowed  — the user granted notification access. Survives almost everything.
#   BOUND    — Android is holding a live binder to NotifService right now.
#              This is the one that matters. Allowed-but-not-bound is R5.
#
# A call is still required to prove firing end to end, but binding can be
# sampled after a reboot, after 24 hours idle, and under battery saver, at no
# cost to anybody's time.

$ErrorActionPreference = 'Continue'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.mosman.thrum'

if (-not (& $adb devices | Select-String -Pattern 'device$')) {
    Write-Host 'No phone connected.' -ForegroundColor Red
    exit 1
}

$now = Get-Date
Write-Host ("=== {0:yyyy-MM-dd HH:mm:ss} ===" -f $now) -ForegroundColor Cyan

# Uptime tells us whether a reboot has happened since the last check, which is
# the difference between "survived a reboot" and "was reinstalled yesterday".
$up = (& $adb shell cat /proc/uptime) -split ' ' | Select-Object -First 1
Write-Host ("phone up for : {0:N1} hours" -f ([double]$up / 3600))

$dump = (& $adb shell dumpsys notification 2>&1) -join "`n"
$allowed = $dump -match "$pkg/$pkg\.NotifService \(user"
$bound = $dump -match "ComponentInfo\{$pkg/$pkg\.NotifService\} \(user \d+\): android\.service\.notification\.INotificationListener"

Write-Host ("allowed      : {0}" -f $(if ($allowed) { 'yes' } else { 'NO' })) -ForegroundColor $(if ($allowed) { 'Green' } else { 'Red' })
Write-Host ("BOUND        : {0}" -f $(if ($bound) { 'yes' } else { 'NO — this is R5' })) -ForegroundColor $(if ($bound) { 'Green' } else { 'Red' })

$saver = (& $adb shell settings get global low_power)
Write-Host ("battery saver: {0}" -f $(if ($saver -eq '1') { 'ON' } else { 'off' }))
Write-Host ("ringer       : {0}" -f $(& $adb shell settings get global mode_ringer))

# The app's own record, which carries what a call actually did. Only readable on
# a debug build; a release build is not debuggable, which is correct and is why
# the soak runs on debug.
$xml = & $adb shell run-as $pkg cat /data/data/$pkg/shared_prefs/thrum.xml 2>&1
if ($xml -is [array]) { $xml = $xml -join "`n" }
if ($xml -match '^\s*<\?xml') {
    $raw = ([xml]$xml).map.string | Where-Object { $_.name -eq 'events' }
    if ($raw) {
        $lines = ($raw.'#text' -split "`n") | Where-Object { $_ -match '\S' }
        Write-Host "recent events:"
        $lines | Select-Object -Last 6 | ForEach-Object {
            $p = $_ -split '\|'
            $t = [datetimeoffset]::FromUnixTimeMilliseconds([int64]$p[1]).ToLocalTime().DateTime
            Write-Host ("  {0:MM-dd HH:mm:ss}  {1,-8} {2,6}ms  {3}" -f $t, $p[2], $p[4], $p[5])
        }
    }
} else {
    Write-Host 'events       : unreadable (release build is not debuggable)' -ForegroundColor Yellow
}
