# Esca Agnellis bauen

## Voraussetzungen

- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools 35
- Git
- Python 3
- der im Repository enthaltene Gradle Wrapper

Die benötigten Maven-Abhängigkeiten werden aus den in `settings.gradle` deklarierten öffentlichen Repositories bezogen. Der Build benötigt keine maintainer-private Ablage, keine Signierschlüssel und keinen Cloud-Dienst.

## Debug-Build

POSIX / Linux:

```text
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Windows-Beitragende können entsprechend `gradlew.bat` verwenden.

Das Debug-Paket verwendet `com.k2040.escaagnellis.debug` und ist für Entwicklung und lokale Tests bestimmt.

## Unsigned Release-Build / F-Droid

Ohne `ESCA_SIGNING_PROPERTIES` kann der normale Build-Typ `release` als unsignierter, optimierter Release-Build erstellt werden.

POSIX / Linux:

```text
./gradlew clean testReleaseUnitTest lintRelease assembleRelease
```

Windows-Beitragende können entsprechend `gradlew.bat` verwenden.

Der Build ist nicht debuggable, verwendet R8/Optimierung und Ressourcenverkleinerung und benötigt keine private Signierkonfiguration. F-Droid verwendet ebenfalls den normalen `release`-Build-Typ, entfernt die reguläre Android-Signierkonfiguration beim eigenen Quell-Build und signiert den veröffentlichten APK anschließend unabhängig.

## Entwickler-Release-Build

Für einen von K2040 signierten Entwickler-Release wird derselbe Build-Typ `release` verwendet. Wenn `ESCA_SIGNING_PROPERTIES` auf eine gültige lokale Signierkonfiguration zeigt, wird diese Konfiguration dem Release-Build zugeordnet. Schlüsselmaterial und Signierkonfiguration gehören nicht in das Repository.

Ein lokaler `release`-Build ohne diese Umgebungsvariable ist absichtlich möglich und bleibt unsigniert. Vor der Veröffentlichung eines Entwickler-Builds muss die vorgesehene Signatur deshalb unabhängig geprüft werden; ein erfolgreicher `assembleRelease`-Task allein ist kein Veröffentlichungsnachweis.

## Ausgaben

Die APK-Namen folgen diesem Muster:

```text
app/build/outputs/apk/<buildType>/Esca-Agnellis-v0.17.0-vc41-<buildType>.apk
```

Build-, APK-, Mapping-, Lint- und Testausgaben sind generiert und werden nicht versioniert.

## Repository-Prüfungen

Der Linux-Maintainer-Workflow verwendet Python 3:

```text
python3 tools/verify-localizations.py
python3 tools/verify-fdroid-hardening.py
```

`verify-fdroid-hardening.py` prüft standardmäßig den unsignierten `release`-Build als F-Droid-Quell-Build-Kandidaten. Für eine Entwickler-Release-APK kann zusätzlich `tools/verify-release-hardening.py` mit `--expected-cert-sha256` und dem erwarteten Zertifikat aufgerufen werden.

Die älteren PowerShell-Prüfungen dürfen für Windows-Beitragende als Kompatibilitätsroute bestehen bleiben, sind aber keine Voraussetzung für den Maintainer-Build oder die öffentliche Quellprüfung.

## Erwartete Produktgrenzen

Ein akzeptierter Build muss insbesondere bestätigen:

- Paket `com.k2040.escaagnellis` für `release`;
- Version `0.17.0`, versionCode `41`;
- minSdk 26 und targetSdk 35;
- nicht debuggable für `release`;
- keine Internet-Berechtigung;
- keine eingebetteten privaten Build-, Schlüssel-, Sicherungs- oder Mapping-Dateien.

## GitHub Actions

Der lokale Build ist die maßgebliche Route für diese Anleitung. Das Repository enthält keinen automatisch ausgelösten Workflow und setzt keinen Cloud-CI-Dienst voraus.
