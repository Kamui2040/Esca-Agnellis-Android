param(
    [ValidateNotNullOrEmpty()]
    [string] $ApkPath = "app/build/outputs/apk/release/Esca-Agnellis-v0.16.0-vc40-release.apk",
    [ValidateNotNullOrEmpty()]
    [string] $MappingPath = "app/build/outputs/mapping/release/mapping.txt",
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedPackage = "com.k2040.escaagnellis",
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedVersionName = "0.16.0",
    [ValidateRange(1, [int]::MaxValue)]
    [int] $ExpectedVersionCode = 40
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptRoot "..")
Set-Location $RepoRoot

function Write-Ok {
    param([string] $Message)
    Write-Host "[OK] $Message"
}

function Assert-RequiredText {
    param(
        [string] $Value,
        [string] $Description
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Description must not be empty."
    }
}

function ConvertTo-AbsolutePath {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return Join-Path $RepoRoot $Path
}

function Invoke-AndroidTool {
    param(
        [string] $ToolPath,
        [string[]] $Arguments
    )

    $output = & $ToolPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "$([System.IO.Path]::GetFileName($ToolPath)) failed with exit code ${LASTEXITCODE}: $($output -join "`n")"
    }

    return $output
}

function Get-AndroidSdkPath {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME
    )

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate "build-tools"))) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Android SDK build-tools were not found. Set ANDROID_SDK_ROOT or ANDROID_HOME."
}

function Get-LatestBuildToolsPath {
    param([string] $SdkPath)

    $buildToolsRoot = Join-Path $SdkPath "build-tools"
    $versions = Get-ChildItem $buildToolsRoot -Directory | Sort-Object -Property @{
        Expression = {
            try {
                [version] $_.Name
            } catch {
                [version] "0.0"
            }
        }
    } -Descending

    if (!$versions) {
        throw "No Android SDK build-tools versions were found under $buildToolsRoot."
    }

    return $versions[0].FullName
}

function Get-AndroidBuildToolPath {
    param(
        [string] $BuildToolsPath,
        [string[]] $CandidateNames
    )

    foreach ($name in $CandidateNames) {
        $candidate = Join-Path $BuildToolsPath $name
        if (Test-Path $candidate -PathType Leaf) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Required Android build tool was not found under $BuildToolsPath. Tried: $($CandidateNames -join ', ')"
}

function Get-RelativeGitPath {
    param([string] $AbsolutePath)

    $relative = Resolve-Path -Relative $AbsolutePath
    return ($relative -replace '^[.][\\/]', '') -replace '\\', '/'
}

function Assert-GitIgnored {
    param([string] $AbsolutePath)

    $relativePath = Get-RelativeGitPath $AbsolutePath
    & git check-ignore -q -- $relativePath
    if ($LASTEXITCODE -ne 0) {
        throw "Expected private build artifact is not ignored by Git: $relativePath"
    }
}

function Assert-GitUntracked {
    param([string] $RelativePath)

    $tracked = & git ls-files -- $RelativePath
    if ($tracked) {
        throw "Private build artifact is tracked by Git: $RelativePath"
    }
}

function Assert-NoTrackedPrivateArtifacts {
    $forbiddenTrackedPatterns = @(
        '(^|/)build/',
        '\.(apk|aab|jks|keystore|p12|pfx|pem|key)$',
        '(^|/)(local|keystore)\.properties$',
        '(^|/)(mapping|seeds|usage|configuration|resources)\.txt$',
        '(^|/).*(backup|sicherung).*\.json$'
    )

    foreach ($relativePath in @(& git ls-files)) {
        foreach ($pattern in $forbiddenTrackedPatterns) {
            if ($relativePath -match $pattern) {
                throw "Tracked private or release artifact is forbidden: $relativePath"
            }
        }
    }
}

function Get-RequiredBadgingValue {
    param(
        [string] $Line,
        [string] $Name
    )

    $match = [regex]::Match(
        $Line,
        "(?:^|\s)$([regex]::Escape($Name))='([^']*)'"
    )

    if (!$match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
        throw "Package metadata field '$Name' was missing or empty."
    }

    return $match.Groups[1].Value
}

function Assert-MappingHasObfuscatedAppClasses {
    param([string] $Path)

    $classPattern = '^(com\.k2040\.escaagnellis\.[^ ]+) -> ([^:]+):$'
    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        if ($line -match $classPattern -and $Matches[1] -ne $Matches[2]) {
            return
        }
    }

    throw "R8 mapping did not contain renamed application class entries."
}

function Assert-ApkArchiveIsReadable {
    param([string] $Path)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entryNames = @($zip.Entries | ForEach-Object { $_.FullName })
        foreach ($requiredEntry in @("AndroidManifest.xml", "classes.dex", "resources.arsc")) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "F-Droid APK is missing required entry: $requiredEntry"
            }
        }

        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) {
                continue
            }

            $stream = $entry.Open()
            try {
                $stream.CopyTo([System.IO.Stream]::Null)
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
}

function Assert-ApkEntriesAreClean {
    param([string] $Path)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $forbiddenEntryPatterns = @(
            '(^|/)mapping\.txt$',
            '(^|/)seeds\.txt$',
            '(^|/)usage\.txt$',
            '(^|/)configuration\.txt$',
            '(^|/)resources\.txt$',
            '(^|/)local\.properties$',
            '(^|/)keystore\.properties$',
            '\.jks$',
            '\.keystore$',
            '\.p12$',
            '\.apk$',
            '\.aab$',
            '(^|/).*backup.*\.json$',
            '(^|/).*sicherung.*\.json$',
            '^META-INF/MANIFEST\.MF$',
            '^META-INF/.*\.(RSA|DSA|EC|SF)$'
        )

        foreach ($entry in $zip.Entries) {
            foreach ($pattern in $forbiddenEntryPatterns) {
                if ($entry.FullName -match $pattern) {
                    throw "F-Droid APK contains forbidden packaged or signing artifact: $($entry.FullName)"
                }
            }
        }
    } finally {
        $zip.Dispose()
    }
}

function Assert-NoStrongPrivateMarkers {
    param([string] $Path)

    $checks = @(
        [pscustomobject]@{
            Label = "windows_private_path"
            Pattern = [regex]::new('(?i)[A-Z]:\\(?:Users|Projects)\\[^\x00\r\n]{1,260}')
        },
        [pscustomobject]@{
            Label = "posix_private_path"
            Pattern = [regex]::new('(?i)/(?:home|Users|Projects)/[^\x00\r\n]{1,260}')
        },
        [pscustomobject]@{
            Label = "esca_signing_environment"
            Pattern = [regex]::new('(?i)\bESCA_SIGNING_PROPERTIES\b')
        },
        [pscustomobject]@{
            Label = "signing_secret_assignment"
            Pattern = [regex]::new('(?i)\b(?:storePassword|keyPassword)\s*[:=]\s*["'']?[^\s"'',;]{1,128}')
        },
        [pscustomobject]@{
            Label = "signing_file_assignment"
            Pattern = [regex]::new('(?i)\bstoreFile\s*[:=]\s*["'']?[^\r\n]{1,260}\.(?:jks|keystore|p12|pfx)\b')
        },
        [pscustomobject]@{
            Label = "keystore_absolute_path"
            Pattern = [regex]::new('(?i)(?:[A-Z]:\\|/)[^\x00\r\n]{0,260}\.(?:jks|keystore|p12|pfx)\b')
        }
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        [long] $totalUncompressed = 0
        [long] $maxEntry = 128MB
        [long] $maxTotal = 512MB

        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/') -or $entry.Length -eq 0) {
                continue
            }
            if ($entry.Length -gt $maxEntry) {
                throw "APK entry exceeds private-marker inspection bound: $($entry.FullName); bytes=$($entry.Length)"
            }
            $totalUncompressed += $entry.Length
            if ($totalUncompressed -gt $maxTotal) {
                throw "APK exceeds cumulative private-marker inspection bound: bytes=$totalUncompressed"
            }

            $stream = $entry.Open()
            $memory = [System.IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                $bytes = $memory.ToArray()
                $views = @(
                    [pscustomobject]@{
                        Name = "latin1"
                        Text = [System.Text.Encoding]::GetEncoding(28591).GetString($bytes)
                    }
                )
                if ($bytes.Length -ge 2) {
                    $views += [pscustomobject]@{
                        Name = "utf16le"
                        Text = [System.Text.Encoding]::Unicode.GetString($bytes)
                    }
                }
                foreach ($view in $views) {
                    foreach ($check in $checks) {
                        if ($check.Pattern.IsMatch($view.Text)) {
                            throw "F-Droid APK contains strong private-build marker '$($check.Label)' at entry:$($entry.FullName) ($($view.Name))."
                        }
                    }
                }
            }
            finally {
                $memory.Dispose()
                $stream.Dispose()
            }
        }
    }
    finally {
        $zip.Dispose()
    }
}

function Assert-ApkIsUnsigned {
    param(
        [string] $ApkSignerPath,
        [string] $Path
    )

    $output = & $ApkSignerPath verify --verbose --print-certs $Path 2>&1
    $exitCode = $LASTEXITCODE
    $outputText = $output -join "`n"

    if ($exitCode -eq 0) {
        throw "F-Droid APK unexpectedly verifies as signed."
    }

    if ($outputText -notmatch '(?im)^DOES NOT VERIFY\s*$') {
        throw "apksigner did not report the expected structurally valid unsigned state: $outputText"
    }
}

$apkAbsolutePath = ConvertTo-AbsolutePath $ApkPath
$mappingAbsolutePath = ConvertTo-AbsolutePath $MappingPath

Assert-RequiredText $ApkPath 'APK path'
Assert-RequiredText $MappingPath 'Mapping path'
Assert-RequiredText $ExpectedPackage 'Expected package'
Assert-RequiredText $ExpectedVersionName 'Expected versionName'

if (!(Test-Path $apkAbsolutePath -PathType Leaf)) {
    throw "F-Droid APK was not found: $ApkPath"
}
Write-Ok "F-Droid APK exists"

if (!(Test-Path $mappingAbsolutePath -PathType Leaf)) {
    throw "F-Droid mapping file was not found: $MappingPath"
}
if ((Get-Item $mappingAbsolutePath).Length -le 0) {
    throw "F-Droid mapping file is empty: $MappingPath"
}
Write-Ok "F-Droid mapping file exists"

$sdkPath = Get-AndroidSdkPath
$buildToolsPath = Get-LatestBuildToolsPath $sdkPath
$aaptPath = Get-AndroidBuildToolPath -BuildToolsPath $buildToolsPath -CandidateNames @("aapt", "aapt.exe")
$apksignerPath = Get-AndroidBuildToolPath -BuildToolsPath $buildToolsPath -CandidateNames @("apksigner", "apksigner.bat", "apksigner.cmd", "apksigner.exe")
$zipalignPath = Get-AndroidBuildToolPath -BuildToolsPath $buildToolsPath -CandidateNames @("zipalign", "zipalign.exe")
Write-Ok "Android build-tools located"

Assert-ApkArchiveIsReadable $apkAbsolutePath
Invoke-AndroidTool $zipalignPath @("-c", "-v", "4", $apkAbsolutePath) | Out-Null
Write-Ok "APK archive is readable, complete, and zip-aligned"

$badging = Invoke-AndroidTool $aaptPath @("dump", "badging", $apkAbsolutePath)
$packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
if (!$packageLine) {
    throw "Package metadata was not found in F-Droid APK badging."
}

$actualPackage = Get-RequiredBadgingValue $packageLine 'name'
$actualVersionCode = Get-RequiredBadgingValue $packageLine 'versionCode'
$actualVersionName = Get-RequiredBadgingValue $packageLine 'versionName'

if ($actualPackage -ne $ExpectedPackage) {
    throw "Unexpected package name in F-Droid APK."
}
if ($actualVersionCode -ne [string] $ExpectedVersionCode) {
    throw "Unexpected versionCode in F-Droid APK."
}
if ($actualVersionName -ne $ExpectedVersionName) {
    throw "Unexpected versionName in F-Droid APK."
}
if ($actualPackage.EndsWith('.debug') -or $actualVersionName.EndsWith('-debug')) {
    throw "F-Droid APK contains a debug package or version suffix."
}
Write-Ok "package and unsuffixed version metadata match expected values"

if ($badging -match "application-debuggable") {
    throw "F-Droid APK is debuggable."
}
Write-Ok "F-Droid APK is non-debuggable"

$permissions = Invoke-AndroidTool $aaptPath @("dump", "permissions", $apkAbsolutePath)
if ($permissions -match "android\.permission\.INTERNET") {
    throw "F-Droid APK requests android.permission.INTERNET."
}
Write-Ok "Internet permission is absent"

Assert-ApkIsUnsigned $apksignerPath $apkAbsolutePath
Assert-ApkEntriesAreClean $apkAbsolutePath
Assert-NoStrongPrivateMarkers $apkAbsolutePath
Write-Ok "F-Droid APK is unsigned and contains no signing or private-build markers"

Assert-MappingHasObfuscatedAppClasses $mappingAbsolutePath
Write-Ok "mapping contains renamed application class entries"

Assert-GitIgnored $apkAbsolutePath
Assert-GitUntracked (Get-RelativeGitPath $apkAbsolutePath)
Write-Ok "F-Droid APK output is ignored and untracked"

Assert-GitIgnored $mappingAbsolutePath
Assert-GitUntracked (Get-RelativeGitPath $mappingAbsolutePath)
Write-Ok "F-Droid mapping output is ignored and untracked"

$mappingDir = Split-Path -Parent $mappingAbsolutePath
$resourcesPath = Join-Path $mappingDir "resources.txt"
if (!(Test-Path $resourcesPath -PathType Leaf)) {
    throw "F-Droid resource-shrinking report was not found: $resourcesPath"
}
if ((Get-Item $resourcesPath).Length -le 0) {
    throw "F-Droid resource-shrinking report is empty: $resourcesPath"
}
Assert-GitIgnored $resourcesPath
Assert-GitUntracked (Get-RelativeGitPath $resourcesPath)
Write-Ok "resource-shrinking output exists, is ignored, and is untracked"

foreach ($privateOutput in @("seeds.txt", "usage.txt", "configuration.txt", "resources.txt")) {
    $privatePath = Join-Path $mappingDir $privateOutput
    if (Test-Path $privatePath -PathType Leaf) {
        Assert-GitIgnored $privatePath
        Assert-GitUntracked (Get-RelativeGitPath $privatePath)
    }
}
Write-Ok "private shrinker outputs are ignored when present"

$debugMappingDir = Join-Path $RepoRoot "app/build/outputs/mapping/debug"
if (Test-Path $debugMappingDir) {
    throw "Debug mapping output exists; debug builds should remain unminified."
}
Write-Ok "debug mapping output is absent"

Assert-NoTrackedPrivateArtifacts
Write-Ok "repository tracks no private or release build artifacts"

Write-Host "F-Droid hardening verification passed."
