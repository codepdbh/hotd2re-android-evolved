[CmdletBinding()]
param(
    [string]$Adb = 'adb',
    [ValidatePattern('^[\w.:-]+$')][string]$Serial,
    [ValidatePattern('^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$')]
    [string]$Package = 'dev.sdlprobe.android',
    [switch]$Multitouch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'android/app/build/outputs/apk/debug/app-debug.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Run scripts/build_android.ps1 first.' }
$testApk = Join-Path $root 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
if ($Multitouch -and !(Test-Path -LiteralPath $testApk)) {
    throw 'Run scripts/build_android.ps1 -WithTests first.'
}
$adbPath = (Get-Command $Adb -CommandType Application | Select-Object -First 1).Source
$selector = @()
if ($Serial) { $selector = @('-s', $Serial) }
$deviceState = & $adbPath @selector get-state
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw 'Use a connected, authorized Android device. Pass -Serial if more than one is attached.'
}
$evidence = New-Item -ItemType Directory -Path (Join-Path $root '.work/device-test') -Force
$component = "$Package/org.libsdl.app.SDLActivity"

function Invoke-Adb {
    & $adbPath @selector @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($args -join ' ')" }
}

function Wait-ProbeLog([string]$Pattern) {
    $deadline = [DateTime]::UtcNow.AddSeconds(16)
    do {
        $appPid = & $adbPath @selector shell pidof -s $Package
        if ($appPid -match '^\d+$') {
            $log = (Invoke-Adb logcat -d "--pid=$appPid" -s 'SDL:V' 'SDL/APP:V' 'SDL/GPU:V' 'AndroidRuntime:E') -join "`n"
            Set-Content -LiteralPath (Join-Path $evidence.FullName 'logcat.txt') -Value $log -Encoding UTF8
            if ($log -match 'probe\.error|FATAL EXCEPTION') { throw 'The diagnostic app reported an error. See logcat.txt.' }
            if ($log -match $Pattern) { return }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Pattern. See .work/device-test/logcat.txt."
}

function Save-ProbeScreenshot([string]$Name) {
    $resumed = (Invoke-Adb shell dumpsys activity activities | Select-String 'topResumedActivity|mResumedActivity') -join "`n"
    if ($resumed -notmatch [regex]::Escape($component)) { throw 'The probe must be in the foreground for capture.' }
    $path = Join-Path $evidence.FullName $Name
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $adbPath
    $start.Arguments = ($selector + @('exec-out', 'screencap', '-p')) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $process = [System.Diagnostics.Process]::Start($start)
    $stream = [System.IO.File]::Create($path)
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'ADB screenshot failed.' }
    } finally {
        $stream.Dispose()
        $process.Dispose()
    }
    return $path
}

Add-Type -AssemblyName System.Drawing
Invoke-Adb install -r $apk
Invoke-Adb shell am force-stop $Package
$before = $null
$after = $null
$resumedBitmap = $null
try {
    Invoke-Adb shell am start -W -n $component
    Wait-ProbeLog 'probe\.ready driver=vulkan pointer_bits=64'
    Wait-ProbeLog 'probe\.frame \d+x\d+ tint=1\.00'
    Wait-ProbeLog 'probe\.audio_ready'
    Start-Sleep -Milliseconds 350
    $before = [System.Drawing.Bitmap]::new((Save-ProbeScreenshot 'initial.png'))
    if ($before.Width -le $before.Height) { throw 'The diagnostic activity did not stay in landscape.' }
    $x = [int]($before.Width / 2 - $before.Height / 8)
    $y = [int]($before.Height / 4)
    Invoke-Adb shell input tap $x $y
    Wait-ProbeLog 'probe\.touch'
    Wait-ProbeLog 'probe\.audio_queued samples=5760'
    Wait-ProbeLog 'probe\.frame \d+x\d+ tint=0\.45'
    Start-Sleep -Milliseconds 350
    $after = [System.Drawing.Bitmap]::new((Save-ProbeScreenshot 'after-touch.png'))
    $beforeColor = $before.GetPixel($x, $y)
    $afterColor = $after.GetPixel($x, $y)
    if ($beforeColor.R -le $afterColor.R + 10) { throw 'The screenshot did not show the expected texture tint.' }

    Invoke-Adb shell input keyevent KEYCODE_HOME
    Wait-ProbeLog 'onStop\(\)'
    Invoke-Adb shell am start -W -n $component
    Wait-ProbeLog '(?s)probe\.background.*probe\.foreground.*probe\.frame \d+x\d+ tint=0\.45'
    Start-Sleep -Milliseconds 350
    $resumedBitmap = [System.Drawing.Bitmap]::new((Save-ProbeScreenshot 'resumed.png'))
    $resumedX = [int]($resumedBitmap.Width / 2 - $resumedBitmap.Height / 8)
    $resumedY = [int]($resumedBitmap.Height / 4)
    if ($resumedBitmap.GetPixel($resumedX, $resumedY).ToArgb() -ne $afterColor.ToArgb()) {
        throw 'The resumed frame did not retain the expected texture tint.'
    }
    Write-Output "Probe passed. Evidence: $($evidence.FullName)"
} finally {
    if ($before) { $before.Dispose() }
    if ($after) { $after.Dispose() }
    if ($resumedBitmap) { $resumedBitmap.Dispose() }
    & $adbPath @selector shell am force-stop $Package
    if ($LASTEXITCODE -ne 0) { Write-Warning 'ADB could not stop the probe after the test.' }
}

if ($Multitouch) {
    Invoke-Adb install -r $testApk
    $runner = "$Package.test/dev.sdlprobe.android.ProbeTouchInstrumentation"
    $result = (Invoke-Adb shell am instrument -w -r $runner) -join "`n"
    Set-Content -LiteralPath (Join-Path $evidence.FullName 'multitouch-instrumentation.txt') -Value $result -Encoding UTF8
    if ($result -notmatch 'INSTRUMENTATION_RESULT: processId=(\d+)') {
        throw "No Android test process was reported.`n$result"
    }
    $testPid = $Matches[1]
    $touchLog = (Invoke-Adb logcat -d "--pid=$testPid" -s 'SDL:V' 'SDL/APP:V' 'SDL/GPU:V' 'AndroidRuntime:E') -join "`n"
    Set-Content -LiteralPath (Join-Path $evidence.FullName 'multitouch-logcat.txt') -Value $touchLog -Encoding UTF8
    if ($result -notmatch 'SDL_PROBE_MULTITOUCH_PASS' -or $result -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw "Android multitouch instrumentation failed.`n$result"
    }
    if ($touchLog -match 'probe\.error|FATAL EXCEPTION' -or $touchLog -notmatch
        '(?s)probe\.frame \d+x\d+ tint=0\.80 contacts=2.*probe\.touch up active=1.*probe\.touch up active=0.*probe\.background.*probe\.foreground.*probe\.frame \d+x\d+ tint=1\.00 contacts=0') {
        throw 'Unexpected SDL contact lifecycle. See .work/device-test/multitouch-logcat.txt.'
    }
    Write-Output "Multitouch and contact reset passed on Android. Evidence: $($evidence.FullName)"
}
