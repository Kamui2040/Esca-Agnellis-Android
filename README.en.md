<p align="center">
  <img src="docs/images/esca-symbol.png" width="120" alt="Esca Agnellis symbol">
</p>

# Esca Agnellis

**Esca Agnellis** is a German-first Android food-pyramid tracker designed for a friendly, local and low-pressure daily flow.

[Deutsche README](README.md)

## Features

- Daily view with 22 default tiles across six visual levels
- Overview and local statistics
- German, English, Spanish, French and European Portuguese
- System, standard and cozy pastel themes
- Local backup and restore through Android document pickers
- Local PDF reports for a user-selected date range
- Optional companion, disabled by default and isolated from core tracking
- No ads, accounts, telemetry, analytics, cloud synchronization or background collection
- No requested Internet permission

## Privacy

Tracking and companion data remain on the device. Backups and PDF reports are written only to an Android document location selected by the user. See [PRIVACY.md](PRIVACY.md).

## Build and test

The public source is designed for independent local builds. It requires JDK 17, Android SDK 35 and the included Gradle Wrapper.

```text
Windows:
gradlew.bat testDebugUnitTest lintDebug assembleDebug
gradlew.bat testReleaseUnitTest lintRelease assembleRelease

POSIX:
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

The normal `release` build remains buildable without private signing configuration and is unsigned in that case. F-Droid uses this public release-build boundary and independently signs its distributed package.

Further information:

- [Build instructions](docs/BUILDING.md)
- [Independent and reproducible-build status](docs/REPRODUCIBLE_BUILDS.md)
- [Release and signing model](docs/RELEASES.md)

## Official and independently signed builds

Official K2040 developer APKs are distributed through `Kamui2040/K2040-Android-Releases`. F-Droid builds independently from public source and uses its own signing identity.

Both variants use package `com.k2040.escaagnellis`, but Android does not allow one certificate to update an installation signed by another certificate. Before switching, export the supported primary backup and, when the companion is enabled, its separate companion backup. Then manually uninstall, install the other variant and restore each backup through its matching flow. No project tool may automatically uninstall the app or clear app data.

## Licensing

- Application code and project build scripts: GNU GPLv3 (`GPL-3.0-only`) with a GPLv3 §7(b) K2040 attribution-preservation additional term. The specified notice `Copyright (c) 2026 K2040.` must be preserved in K2040-authored material or in the Appropriate Legal Notices displayed by works containing it.
- Cleared documentation and artwork: `CC-BY-4.0` where identified by the controlling files; that licence independently requires the applicable attribution.
- Third-party dependencies and incorporated third-party components: retain their respective controlling licences/notices.
- Gradle Wrapper components: `Apache-2.0`
- Names, logos and official-status rules: [TRADEMARKS.md](TRADEMARKS.md)
- Exact runtime-artwork provenance and attribution: [ASSET_PROVENANCE.yml](ASSET_PROVENANCE.yml); localized store-screenshot provenance: [fastlane/SCREENSHOT_PROVENANCE.md](fastlane/SCREENSHOT_PROVENANCE.md)

The standard GNU GPLv3 text remains unmodified. The K2040 additional term is separate in `LICENSES/GPL-3.0-Section-7b-K2040.txt`. The other controlling texts are in `LICENSE`, `LICENSES/`, `NOTICE.md` and `THIRD_PARTY_NOTICES.md`.

## Orientation and non-affiliation

Esca Agnellis is not an official BZfE or BLE product and is not affiliated with, endorsed by or sponsored by either organization. The app provides general tracking and orientation features, not medical, dietary or individualized nutritional advice.

## Contributing and support

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [SECURITY.md](SECURITY.md)
- [SUPPORT.md](SUPPORT.md)
- [CHANGELOG.md](CHANGELOG.md)
