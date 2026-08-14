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

## Unsigned Release-Build / F-Droid

Ohne `ESCA_SIGNING_PROPERTIES` kann der normale Build-Typ `release` als unsignierter, optimierter Release-Build erstellt werden.

Windows:

```text
gradlew.bat clean testReleaseUnitTest lintRelease assembleRelease
```

POSIX:

```text
./gradlew clean testReleaseUnitTest lintRelease assembleRelease
```

Der Build ist nicht debuggable, verwendet R8/Optimierung und Ressourcenverkleinerung und benötigt keine private Signierkonfiguration. F-Droid verwendet ebenfalls den normalen `release`-Build-Typ, entfernt die reguläre Android-Signierkonfiguration beim eigenen Quell-Build und signiert den veröffentlichten APK anschließend unabhängig.

## Entwickler-Release-Build

Für einen von K2040 signierten Entwickler-Release wird derselbe Build-Typ `release` verwendet. Wenn `ESCA_SIGNING_PROPERTIES` auf eine gültige lokale Signierkonfiguration zeigt, wird diese Konfiguration dem Release-Build zugeordnet. Schlüsselmaterial und Signierkonfiguration gehören nicht in das Repository.

Ein lokaler `release`-Build ohne diese Umgebungsvariable ist absichtlich möglich und bleibt unsigniert. Vor der Veröffentlichung eines Entwickler-Builds muss die vorgesehene Signatur deshalb unabhängig geprüft werden; ein erfolgreicher `assembleRelease`-Task allein ist kein Veröffentlichungsnachweis.

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

`verify-fdroid-hardening.ps1` prüft standardmäßig den unsignierten `release`-Build als F-Droid-Quell-Build-Kandidaten. Für eine Entwickler-Release-APK kann zusätzlich `tools/verify-release-hardening.ps1` mit dem erwarteten Zertifikat aufgerufen werden.

## Erwartete Produktgrenzen

Ein akzeptierter Build muss insbesondere bestätigen:

- Paket `com.k2040.escaagnellis` für `release`;
- Version `0.16.0`, versionCode `40`;
- minSdk 26 und targetSdk 35;
- nicht debuggable für `release`;
- keine Internet-Berechtigung;
- keine eingebetteten privaten Build-, Schlüssel-, Sicherungs- oder Mapping-Dateien.

## GitHub Actions

Der lokale Build ist die maßgebliche Route für diese Anleitung. Die initiale öffentliche Quelle enthält keinen automatisch ausgelösten Workflow und setzt keinen Cloud-CI-Dienst voraus.
