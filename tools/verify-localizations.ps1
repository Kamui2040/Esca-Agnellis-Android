[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$resRoot = Join-Path $repoRoot 'app\src\main\res'
$expectedTranslatableKeyCount = 221

$required = [ordered]@{
    de = 'values'
    en = 'values-en'
    es = 'values-es'
    fr = 'values-fr'
    'pt-PT' = 'values-pt-rPT'
}

$failures = [System.Collections.Generic.List[string]]::new()
$documents = @{}

function Add-Failure([string]$Message) {
    $failures.Add($Message)
    Write-Host "FAIL: $Message" -ForegroundColor Red
}

function Get-Placeholders([string]$Text) {
    $clean = $Text -replace '%%', ''

    return @(
        [regex]::Matches(
            $clean,
            '%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]'
        ) |
            ForEach-Object { $_.Value } |
            Sort-Object
    )
}

function Test-PlaceholderParity(
    [object[]]$Expected,
    [object[]]$Actual,
    [string]$Description
) {
    $expectedText = @($Expected) -join [char]0x1F
    $actualText = @($Actual) -join [char]0x1F

    if ($expectedText -ne $actualText) {
        Add-Failure "Placeholder mismatch: $Description"
    }
}

function Get-ResourceEntries(
    [xml]$Document,
    [string]$Locale
) {
    $entries = [System.Collections.Generic.Dictionary[string, object]]::new(
        [System.StringComparer]::Ordinal
    )

    $nodes = @(
        $Document.SelectNodes(
            '/resources/string | /resources/string-array | /resources/plurals'
        )
    )

    foreach ($node in $nodes) {
        $name = [string]$node.GetAttribute('name')

        if ([string]::IsNullOrWhiteSpace($name)) {
            Add-Failure "$Locale resource entry without a name."
            continue
        }

        if ([string]$node.GetAttribute('translatable') -eq 'false') {
            continue
        }

        if ($entries.ContainsKey($name)) {
            Add-Failure "$Locale duplicate resource key: $name"
            continue
        }

        $kind = [string]$node.LocalName

        switch ($kind) {
            'string' {
                $value = [string]$node.InnerText

                if ([string]::IsNullOrWhiteSpace($value)) {
                    Add-Failure "$Locale empty string value for key: $name"
                }

                $entries.Add(
                    $name,
                    [pscustomobject]@{
                        Kind = 'string'
                        Text = $value
                        Placeholders = @(Get-Placeholders $value)
                        Items = @()
                    }
                )
            }

            'string-array' {
                $items = [System.Collections.Generic.List[object]]::new()
                $itemNodes = @($node.SelectNodes('./item'))

                if ($itemNodes.Count -eq 0) {
                    Add-Failure "$Locale empty string-array resource: $name"
                }

                for ($index = 0; $index -lt $itemNodes.Count; $index++) {
                    $value = [string]$itemNodes[$index].InnerText

                    if ([string]::IsNullOrWhiteSpace($value)) {
                        Add-Failure "$Locale empty string-array item for key: $name index: $index"
                    }

                    $items.Add(
                        [pscustomobject]@{
                            Index = $index
                            Text = $value
                            Placeholders = @(Get-Placeholders $value)
                            Quantity = $null
                        }
                    )
                }

                $entries.Add(
                    $name,
                    [pscustomobject]@{
                        Kind = 'string-array'
                        Text = $null
                        Placeholders = @()
                        Items = @($items)
                    }
                )
            }

            'plurals' {
                $items = [System.Collections.Generic.List[object]]::new()
                $itemNodes = @($node.SelectNodes('./item'))
                $seenQuantities = [System.Collections.Generic.HashSet[string]]::new(
                    [System.StringComparer]::Ordinal
                )

                if ($itemNodes.Count -eq 0) {
                    Add-Failure "$Locale empty plurals resource: $name"
                }

                for ($index = 0; $index -lt $itemNodes.Count; $index++) {
                    $quantity = [string]$itemNodes[$index].GetAttribute('quantity')
                    $value = [string]$itemNodes[$index].InnerText

                    if ([string]::IsNullOrWhiteSpace($quantity)) {
                        Add-Failure "$Locale plurals item missing quantity for key: $name index: $index"
                    }
                    elseif (-not $seenQuantities.Add($quantity)) {
                        Add-Failure "$Locale duplicate plurals quantity for key: $name quantity: $quantity"
                    }

                    if ([string]::IsNullOrWhiteSpace($value)) {
                        Add-Failure "$Locale empty plurals item for key: $name quantity: $quantity index: $index"
                    }

                    $items.Add(
                        [pscustomobject]@{
                            Index = $index
                            Text = $value
                            Placeholders = @(Get-Placeholders $value)
                            Quantity = $quantity
                        }
                    )
                }

                $entries.Add(
                    $name,
                    [pscustomobject]@{
                        Kind = 'plurals'
                        Text = $null
                        Placeholders = @()
                        Items = @($items)
                    }
                )
            }

            default {
                Add-Failure "$Locale unsupported resource kind for key: $name kind: $kind"
            }
        }
    }

    return ,$entries
}

function Compare-ResourceEntry(
    [string]$Locale,
    [string]$Key,
    [object]$Expected,
    [object]$Actual
) {
    if ($Expected.Kind -ne $Actual.Kind) {
        Add-Failure "$Locale resource kind differs for key: $Key (de=$($Expected.Kind), $Locale=$($Actual.Kind))"
        return
    }

    switch ($Expected.Kind) {
        'string' {
            Test-PlaceholderParity `
                @($Expected.Placeholders) `
                @($Actual.Placeholders) `
                "$Locale string key: $Key"
        }

        'string-array' {
            $expectedItems = @($Expected.Items)
            $actualItems = @($Actual.Items)

            if ($expectedItems.Count -ne $actualItems.Count) {
                Add-Failure "$Locale string-array item count differs for key: $Key (de=$($expectedItems.Count), $Locale=$($actualItems.Count))"
                return
            }

            for ($index = 0; $index -lt $expectedItems.Count; $index++) {
                Test-PlaceholderParity `
                    @($expectedItems[$index].Placeholders) `
                    @($actualItems[$index].Placeholders) `
                    "$Locale string-array key: $Key index: $index"
            }
        }

        'plurals' {
            $expectedByQuantity = [System.Collections.Generic.Dictionary[string, object]]::new(
                [System.StringComparer]::Ordinal
            )
            $actualByQuantity = [System.Collections.Generic.Dictionary[string, object]]::new(
                [System.StringComparer]::Ordinal
            )

            foreach ($item in @($Expected.Items)) {
                $expectedByQuantity[$item.Quantity] = $item
            }

            foreach ($item in @($Actual.Items)) {
                $actualByQuantity[$item.Quantity] = $item
            }

            $expectedQuantities = @($expectedByQuantity.Keys | Sort-Object)
            $actualQuantities = @($actualByQuantity.Keys | Sort-Object)

            if (($expectedQuantities -join '|') -ne ($actualQuantities -join '|')) {
                Add-Failure "$Locale plurals quantities differ for key: $Key (de=$($expectedQuantities -join ','), $Locale=$($actualQuantities -join ','))"
                return
            }

            foreach ($quantity in $expectedQuantities) {
                Test-PlaceholderParity `
                    @($expectedByQuantity[$quantity].Placeholders) `
                    @($actualByQuantity[$quantity].Placeholders) `
                    "$Locale plurals key: $Key quantity: $quantity"
            }
        }
    }
}

function Invoke-ExtractionSelfTest {
    [xml]$sample = @'
<resources>
    <string name="plain">Text %1$s</string>
    <string-array name="array">
        <item>One</item>
        <item>Two %1$d</item>
    </string-array>
    <plurals name="plural">
        <item quantity="one">%1$d item</item>
        <item quantity="other">%1$d items</item>
    </plurals>
    <string name="ignored" translatable="false">Brand</string>
</resources>
'@

    $failureCountBefore = $failures.Count
    $entries = Get-ResourceEntries $sample 'self-test'

    if ($failures.Count -ne $failureCountBefore) {
        throw 'Localization verifier extraction self-test emitted a validation failure.'
    }

    if ($entries.Count -ne 3) {
        throw "Localization verifier extraction self-test expected 3 entries, found $($entries.Count)."
    }

    if (@($entries['array'].Items).Count -ne 2) {
        throw 'Localization verifier extraction self-test failed array item extraction.'
    }

    if (@($entries['plural'].Items).Count -ne 2) {
        throw 'Localization verifier extraction self-test failed plural item extraction.'
    }

    if ($entries['plural'].Items[1].Quantity -ne 'other') {
        throw 'Localization verifier extraction self-test failed plural quantity extraction.'
    }

    if ((@($entries['array'].Items[1].Placeholders) -join '|') -ne '%1$d') {
        throw 'Localization verifier extraction self-test failed indexed placeholder extraction.'
    }
}

Invoke-ExtractionSelfTest

if (Test-Path -LiteralPath (Join-Path $resRoot 'values-pt')) {
    Add-Failure 'Generic values-pt is forbidden; use values-pt-rPT only.'
}

foreach ($locale in $required.Keys) {
    $directory = Join-Path $resRoot $required[$locale]
    $path = Join-Path $directory 'strings.xml'

    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        Add-Failure "Missing locale directory: $($required[$locale])"
        continue
    }

    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Missing strings file: $path"
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($path)

    if ($bytes.Length -ge 3 -and
        $bytes[0] -eq 0xEF -and
        $bytes[1] -eq 0xBB -and
        $bytes[2] -eq 0xBF) {
        Add-Failure "UTF-8 BOM is not allowed: $path"
    }

    try {
        $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
        $content = $utf8.GetString($bytes)
    }
    catch {
        Add-Failure "Invalid UTF-8: $path"
        continue
    }

    $mojibakePattern = '\u00C3.|\u00C2.|\u00E2\u20AC[\u2122\u0153\u017E\u201C\u201D]|\u00F0\u0178|\u00EF\u00BF\u00BD|\uFFFD'

    if ([regex]::IsMatch(
        $content,
        $mojibakePattern,
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )) {
        Add-Failure "Obvious mojibake marker found: $path"
    }

    try {
        $documents[$locale] = [xml]$content
    }
    catch {
        Add-Failure "Invalid XML in $path`: $($_.Exception.Message)"
    }
}

if (-not $documents.ContainsKey('de')) {
    Add-Failure 'German default resources could not be loaded.'
}
else {
    $defaultEntries = Get-ResourceEntries $documents['de'] 'de'

    if ($defaultEntries.Count -ne $expectedTranslatableKeyCount) {
        Add-Failure "de translatable key count must be $expectedTranslatableKeyCount, found $($defaultEntries.Count)"
    }

    foreach ($locale in $required.Keys) {
        if (-not $documents.ContainsKey($locale)) {
            continue
        }

        $entries = Get-ResourceEntries $documents[$locale] $locale

        if ($entries.Count -ne $expectedTranslatableKeyCount) {
            Add-Failure "$locale translatable key count must be $expectedTranslatableKeyCount, found $($entries.Count)"
        }

        $expectedKeys = @($defaultEntries.Keys | Sort-Object)
        $actualKeys = @($entries.Keys | Sort-Object)

        foreach ($key in $expectedKeys) {
            if (-not $entries.ContainsKey($key)) {
                Add-Failure "$locale missing key: $key"
            }
        }

        foreach ($key in $actualKeys) {
            if (-not $defaultEntries.ContainsKey($key)) {
                Add-Failure "$locale has unexpected translatable key: $key"
            }
        }

        foreach ($key in $expectedKeys) {
            if (-not $entries.ContainsKey($key)) {
                continue
            }

            Compare-ResourceEntry `
                $locale `
                $key `
                $defaultEntries[$key] `
                $entries[$key]
        }

        Write-Host ("{0}: {1} translatable keys" -f $locale, $entries.Count)
    }
}

if ($failures.Count -gt 0) {
    Write-Host (
        "Localization verification failed with {0} issue(s)." -f
        $failures.Count
    ) -ForegroundColor Red

    exit 1
}

Write-Host 'Localization verification passed.' -ForegroundColor Green
