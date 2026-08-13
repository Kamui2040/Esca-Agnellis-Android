param(
    [ValidateNotNullOrEmpty()]
    [string] $ApkPath = "app\build\outputs\apk\release\Esca-Agnellis-v0.16.0-vc40-release.apk",

    [ValidateNotNullOrEmpty()]
    [string] $MappingPath = "app\build\outputs\mapping\release\mapping.txt",

    [ValidateNotNullOrEmpty()]
    [string] $ExpectedPackage = "com.k2040.escaagnellis",

    [ValidateNotNullOrEmpty()]
    [string] $ExpectedVersionName = "0.16.0",

    [ValidateRange(1, [int]::MaxValue)]
    [int] $ExpectedVersionCode = 40,

    [Parameter(Mandatory)]
    [ValidatePattern("^[0-9A-Fa-f]{64}$")]
    [string] $ExpectedCertSha256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptRoot "..")).Path
Set-Location $RepoRoot

function Write-Ok {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host "[OK] $Message"
}

function ConvertTo-AbsolutePath {
    param([Parameter(Mandatory)][string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $RepoRoot $Path
}

function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory)][string] $FilePath,
        [Parameter(Mandatory)][string[]] $Arguments
    )

    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    try {
        & $FilePath @Arguments 1> $stdout 2> $stderr
        $code = $LASTEXITCODE
        $out = @(Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue)
        $err = @(Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue)
        if ($code -ne 0) {
            throw "$([System.IO.Path]::GetFileName($FilePath)) failed with exit code $code.`nSTDOUT:`n$($out -join "`n")`nSTDERR:`n$($err -join "`n")"
        }
        return $out
    }
    finally {
        Remove-Item -LiteralPath $stdout, $stderr -Force -ErrorAction SilentlyContinue
    }
}

function Get-AndroidBuildTools {
    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $sdkCandidates) {
        $root = Join-Path $candidate "build-tools"
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            continue
        }

        $versions = @(
            Get-ChildItem -LiteralPath $root -Directory |
                Sort-Object -Property @{
                    Expression = {
                        try { [version]$_.Name }
                        catch { [version]"0.0" }
                    }
                } -Descending
        )

        if ($versions.Count -gt 0) {
            return $versions[0].FullName
        }
    }

    throw "Android SDK build-tools were not found. Set ANDROID_SDK_ROOT or ANDROID_HOME."
}

function Get-BadgingValue {
    param(
        [Parameter(Mandatory)][string] $Line,
        [Parameter(Mandatory)][string] $Name
    )

    $match = [regex]::Match(
        $Line,
        "(?:^|\s)$([regex]::Escape($Name))='([^']*)'"
    )
    if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
        throw "Package metadata field '$Name' was missing or empty."
    }
    return $match.Groups[1].Value
}

function Assert-MappingHasRenamedAppClass {
    param([Parameter(Mandatory)][string] $Path)

    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        if ($line -match "^(com\.k2040\.escaagnellis\.[^ ]+) -> ([^:]+):$" -and
                $Matches[1] -ne $Matches[2]) {
            return
        }
    }

    throw "R8 mapping contains no renamed application class."
}

function Assert-NoForbiddenZipEntries {
    param([Parameter(Mandatory)][string] $Path)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entryNames = @($zip.Entries | ForEach-Object { $_.FullName })
        foreach ($requiredEntry in @("AndroidManifest.xml", "classes.dex", "resources.arsc")) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "APK is missing required entry: $requiredEntry"
            }
        }

        $patterns = @(
            "(^|/)(mapping|seeds|usage|configuration|resources)\.txt$",
            "(^|/)(local|keystore)\.properties$",
            "\.(jks|keystore|p12|pfx|pem|key|apk|aab)$",
            "(^|/).*(backup|sicherung).*\.json$"
        )

        foreach ($entry in $zip.Entries) {
            foreach ($pattern in $patterns) {
                if ($entry.FullName -match $pattern) {
                    throw "APK contains forbidden generated or private artifact: $($entry.FullName)"
                }
            }

            if ($entry.FullName.EndsWith('/') -or $entry.Length -eq 0) {
                continue
            }

            $stream = $entry.Open()
            try {
                $stream.CopyTo([System.IO.Stream]::Null)
            }
            finally {
                $stream.Dispose()
            }
        }
    }
    finally {
        $zip.Dispose()
    }
}

function Assert-NoStrongPrivateMarkers {
    param([Parameter(Mandatory)][string] $Path)

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

    $inspectBytes = {
        param(
            [Parameter(Mandatory)][byte[]] $Bytes,
            [Parameter(Mandatory)][string] $Location
        )

        $views = @(
            [pscustomobject]@{
                Name = "latin1"
                Text = [System.Text.Encoding]::GetEncoding(28591).GetString($Bytes)
            }
        )
        if ($Bytes.Length -ge 2) {
            $views += [pscustomobject]@{
                Name = "utf16le"
                Text = [System.Text.Encoding]::Unicode.GetString($Bytes)
            }
        }

        foreach ($view in $views) {
            foreach ($check in $checks) {
                if ($check.Pattern.IsMatch($view.Text)) {
                    throw "APK contains strong private-build marker '$($check.Label)' at $Location ($($view.Name))."
                }
            }
        }
    }

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
                & $inspectBytes -Bytes $memory.ToArray() -Location "entry:$($entry.FullName)"
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

    & $inspectBytes -Bytes ([System.IO.File]::ReadAllBytes($Path)) -Location "raw_archive"
}

$apk = ConvertTo-AbsolutePath $ApkPath
$mapping = ConvertTo-AbsolutePath $MappingPath

if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
    throw "Release APK was not found: $ApkPath"
}
if (-not (Test-Path -LiteralPath $mapping -PathType Leaf)) {
    throw "Release mapping file was not found: $MappingPath"
}
if ((Get-Item -LiteralPath $mapping).Length -le 0) {
    throw "Release mapping file is empty: $MappingPath"
}
Write-Ok "release APK and mapping exist"

$buildTools = Get-AndroidBuildTools
$aapt = Join-Path $buildTools "aapt.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"

foreach ($tool in @($aapt, $apksigner)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Required Android build tool was not found: $tool"
    }
}
Write-Ok "Android build-tools located"

$badging = @(Invoke-NativeChecked -FilePath $aapt -Arguments @("dump", "badging", $apk))
$packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($packageLine)) {
    throw "Package metadata was not found."
}

$actualPackage = Get-BadgingValue -Line $packageLine -Name "name"
$actualVersionCode = Get-BadgingValue -Line $packageLine -Name "versionCode"
$actualVersionName = Get-BadgingValue -Line $packageLine -Name "versionName"

if ($actualPackage -ne $ExpectedPackage) {
    throw "Unexpected package name: $actualPackage"
}
if ($actualVersionCode -ne [string]$ExpectedVersionCode) {
    throw "Unexpected versionCode: $actualVersionCode"
}
if ($actualVersionName -ne $ExpectedVersionName) {
    throw "Unexpected versionName: $actualVersionName"
}
if ($badging -match "application-debuggable") {
    throw "Release APK is debuggable."
}
Write-Ok "package, version and non-debuggable state match"

$permissions = @(Invoke-NativeChecked -FilePath $aapt -Arguments @("dump", "permissions", $apk))
if ($permissions -match "android\.permission\.INTERNET") {
    throw "Release APK requests android.permission.INTERNET."
}
Write-Ok "Internet permission is absent"

$signature = @(Invoke-NativeChecked -FilePath $apksigner -Arguments @("verify", "--verbose", "--print-certs", $apk))
$signatureText = $signature -join "`n"
$fingerprintMatch = [regex]::Match($signatureText, "(?im)SHA-256 digest:\s*([0-9A-Fa-f]{64})")
if (-not $fingerprintMatch.Success) {
    throw "Release certificate SHA-256 fingerprint was not found."
}

$actualCert = $fingerprintMatch.Groups[1].Value.ToUpperInvariant()
if ($actualCert -ne $ExpectedCertSha256.ToUpperInvariant()) {
    throw "Release certificate SHA-256 fingerprint does not match."
}
Write-Ok "release certificate fingerprint matches"

Assert-MappingHasRenamedAppClass -Path $mapping
Write-Ok "R8 mapping contains renamed application classes"

Assert-NoForbiddenZipEntries -Path $apk
Assert-NoStrongPrivateMarkers -Path $apk
Write-Ok "APK contains no forbidden private artifacts or strong private markers"

Write-Host "RELEASE_HARDENING_VERDICT: PASSED"
Write-Host "PACKAGE: $actualPackage"
Write-Host "VERSION_NAME: $actualVersionName"
Write-Host "VERSION_CODE: $actualVersionCode"
Write-Host "CERT_SHA256: $actualCert"
