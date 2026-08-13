# Esca Agnellis bauen

## Voraussetzungen

- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools 35
- Git
- der im Repository enthaltene Gradle Wrapper

Die benötigten Maven-Abhängigkeiten werden aus den in `settings.gradle` deklarierten öffentlichen Repositories bezogen. Der Build benötigt keine maintainer-private Ablage, keine Signierschlüssel und keinen Cloud-Dienst.

## Debug-Build

Windows:

```text
gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

POSIX:

```text
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Das Debug-Paket verwendet `com.k2040.escaagnellis.debug` und ist für Entwicklung und lokale Tests bestimmt.

## Unsigned F-Droid-Build

Windows:

```text
gradlew.bat clean testFdroidUnitTest lintFdroid assembleFdroid
```

POSIX:

```text
./gradlew clean testFdroidUnitTest lintFdroid assembleFdroid
```

Der Build-Typ `fdroid` verwendet Optimierung und Ressourcenverkleinerung, ist nicht debuggable und besitzt absichtlich keine lokale Release-Signatur. F-Droid signiert seinen veröffentlichten Build unabhängig.

## Entwickler-Release-Build

Der Build-Typ `release` verlangt eine lokale Signierkonfiguration über `ESCA_SIGNING_PROPERTIES`. Diese Konfiguration und das Schlüsselmaterial gehören nicht in das Repository und sind für Debug- oder F-Droid-Builds nicht erforderlich.

## Ausgaben

Die APK-Namen folgen diesem Muster:

```text
app/build/outputs/apk/<buildType>/Esca-Agnellis-v0.16.0-vc40-<buildType>.apk
```

Build-, APK-, Mapping-, Lint- und Testausgaben sind generiert und werden nicht versioniert.

## Repository-Prüfungen

Windows PowerShell:

```text
pwsh -NoProfile -File tools/verify-localizations.ps1
pwsh -NoProfile -File tools/verify-fdroid-hardening.ps1
```

Für eine Entwickler-Release-APK kann zusätzlich `tools/verify-release-hardening.ps1` mit dem erwarteten Zertifikat aufgerufen werden.

## Erwartete Produktgrenzen

Ein akzeptierter Build muss insbesondere bestätigen:

- Paket `com.k2040.escaagnellis` für `release` und `fdroid`;
- Version `0.16.0`, versionCode `40`;
- minSdk 26 und targetSdk 35;
- nicht debuggable für `release` und `fdroid`;
- keine Internet-Berechtigung;
- keine eingebetteten privaten Build-, Schlüssel-, Sicherungs- oder Mapping-Dateien.

## GitHub Actions

Der lokale Build ist die maßgebliche Route für diese Anleitung. Die initiale öffentliche Quelle enthält keinen automatisch ausgelösten Workflow und setzt keinen Cloud-CI-Dienst voraus.
