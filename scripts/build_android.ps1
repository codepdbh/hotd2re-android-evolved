[CmdletBinding()]
param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [switch]$Offline,
    [switch]$WithTests,
    [switch]$Game,
    [switch]$Release,
    # armeabi-v7a builds the experimental 32-bit APK (no custom Vulkan drivers).
    [ValidateSet('arm64-v8a', 'armeabi-v7a')][string]$Abi = 'arm64-v8a'
)

$ErrorActionPreference = 'Stop'
if ($Release -and (!$Game -or $WithTests)) {
    throw 'Use -Game -Release to build the unsigned game release.'
}
$root = Split-Path -Parent $PSScriptRoot
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (!$Jdk) { $Jdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr' }

foreach ($component in 'platforms/android-35/android.jar', 'build-tools/34.0.0/aapt2.exe',
    'ndk/28.2.13676358/source.properties', 'cmake/3.30.5/bin/cmake.exe') {
    if (!(Test-Path -LiteralPath (Join-Path $Sdk $component) -PathType Leaf)) {
        throw "Missing Android SDK component $component in $Sdk. Pass -Sdk to select an existing SDK."
    }
}
if (!(Test-Path -LiteralPath (Join-Path $Jdk 'bin/javac.exe') -PathType Leaf)) {
    throw 'Pass -Jdk or set JAVA_HOME to a JDK between 17 and 22.'
}
$jdkReleaseInfo = Get-Content -Raw -LiteralPath (Join-Path $Jdk 'release')
if ($jdkReleaseInfo -notmatch '(?m)^JAVA_VERSION="(17|18|19|20|21|22)([.+-]|")') {
    throw 'Gradle 8.9 and AGP 8.7.3 need a JDK between 17 and 22. Pass -Jdk to select one.'
}

$deps = New-Item -ItemType Directory -Path (Join-Path $root '.work/deps') -Force
$archive = Join-Path $deps.FullName 'SDL3-3.4.18.tar.gz'
$sha256 = '9c75cf16330322c217dedd2e0609f1124f1b54b8633e763467b4684d0f4334a3'
if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive).Hash -ne $sha256) {
    if ($Offline) { throw 'The verified SDL archive is required in .work/deps in offline mode.' }
    $url = 'https://github.com/libsdl-org/SDL/releases/download/release-3.4.18/SDL3-3.4.18.tar.gz'
    & curl.exe --fail --location --silent --show-error --output $archive $url
    if ($LASTEXITCODE -ne 0) { throw 'SDL download failed.' }
}
if ((Get-FileHash -LiteralPath $archive).Hash -ne $sha256) { throw 'Unexpected SDL archive SHA-256.' }

$source = Join-Path $deps.FullName 'SDL3-3.4.18'
$marker = Join-Path $source '.archive-sha256'
if (!(Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw).Trim() -ne $sha256) {
    & tar.exe -xf $archive -C $deps.FullName
    if ($LASTEXITCODE -ne 0) { throw 'SDL extraction failed.' }
    Set-Content -LiteralPath $marker -Value $sha256 -Encoding ASCII
}

# libadrenotools (custom Vulkan drivers) and its linker namespace helper, pinned like SDL.
foreach ($dep in @(
        @{ Name = 'libadrenotools'; Commit = '8fae8ce254dfc1344527e05301e43f37dea2df80';
           Sha = 'ceffce971676d4cfdf348a082df06fc92a1dca6d95bea892a480d63f200961cb' },
        @{ Name = 'liblinkernsbypass'; Commit = 'aa3975893d83ef1bc84c321ec60c65fbf1287887';
           Sha = 'da1128c8aa771c4d24766b53a47822d0717baa5c536ca8491220402942b80638' })) {
    $depArchive = Join-Path $deps.FullName "$($dep.Name)-$($dep.Commit).tar.gz"
    if (!(Test-Path -LiteralPath $depArchive) -or (Get-FileHash -LiteralPath $depArchive).Hash -ne $dep.Sha) {
        if ($Offline) { throw "The verified $($dep.Name) archive is required in .work/deps in offline mode." }
        & curl.exe --fail --location --silent --show-error --output $depArchive "https://github.com/bylaws/$($dep.Name)/archive/$($dep.Commit).tar.gz"
        if ($LASTEXITCODE -ne 0) { throw "$($dep.Name) download failed." }
    }
    if ((Get-FileHash -LiteralPath $depArchive).Hash -ne $dep.Sha) { throw "Unexpected $($dep.Name) archive SHA-256." }
    $depSource = Join-Path $deps.FullName "$($dep.Name)-$($dep.Commit)"
    $depMarker = Join-Path $depSource '.archive-sha256'
    if (!(Test-Path -LiteralPath $depMarker) -or (Get-Content -LiteralPath $depMarker -Raw).Trim() -ne $dep.Sha) {
        & tar.exe -xf $depArchive -C $deps.FullName
        if ($LASTEXITCODE -ne 0) { throw "$($dep.Name) extraction failed." }
        Set-Content -LiteralPath $depMarker -Value $dep.Sha -Encoding ASCII
    }
}

$savedSdk = $env:ANDROID_HOME
$savedLegacySdk = $env:ANDROID_SDK_ROOT
$savedJdk = $env:JAVA_HOME
try {
    $env:ANDROID_HOME = (Resolve-Path -LiteralPath $Sdk).Path
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk).Path
    $module = if ($Game) { 'game' } else { 'app' }
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    $gradleArgs = @('-p', (Join-Path $root 'android'), '--no-daemon', '--console=plain', ":${module}:assemble${variant}")
    if ($Game) { $gradleArgs += "-Photd2Abi=$Abi" }
    if ($WithTests) { $gradleArgs += ':app:assembleDebugAndroidTest' }
    if ($Offline) { $gradleArgs += '--offline' }
    # Windows PowerShell turns redirected native stderr into error records.
    # Gradle also uses stderr for compiler notes, so use its exit code.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $root 'android/gradlew.bat') @gradleArgs 2>&1 | ForEach-Object { $_.ToString() }
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
} finally {
    $ErrorActionPreference = 'Stop'
    $env:ANDROID_HOME = $savedSdk
    $env:ANDROID_SDK_ROOT = $savedLegacySdk
    $env:JAVA_HOME = $savedJdk
}

$artifact = if ($Release) { 'release/game-release-unsigned.apk' } else { "debug/$module-debug.apk" }
Get-FileHash -LiteralPath (Join-Path $root "android/$module/build/outputs/apk/$artifact")
