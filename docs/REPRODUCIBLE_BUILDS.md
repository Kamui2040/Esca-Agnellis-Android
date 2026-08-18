# Unabhängige und reproduzierbare Builds

## Aktueller Anspruch

Esca Agnellis ist für **gepinntes und unabhängiges Bauen aus öffentlicher Quelle** vorbereitet. Derzeit wird keine vollständige bitgenaue Reproduzierbarkeit verschiedener Maschinen oder Zeitpunkte behauptet.

## Gepinnte Build-Grundlage

- Gradle Wrapper 8.9
- Android Gradle Plugin 8.7.3
- Gradle-Distributions-SHA-256 in `gradle/wrapper/gradle-wrapper.properties`
- compileSdk und targetSdk 35
- JDK 17 als unterstützte Java-Laufzeit
- JUnit 4.13.2 ausschließlich für lokale Tests
- keine deklarierte Drittanbieter-Runtime-Bibliothek im Anwendungspaket

Die kontrollierende Abhängigkeits- und Lizenzübersicht steht in `THIRD_PARTY_NOTICES.md`.

## Unabhängiger Build

Ein unabhängiger Prüfer benötigt nur:

1. den exakten öffentlichen Quellstand;
2. JDK 17;
3. Android SDK 35;
4. Netzwerkzugriff auf die deklarierten öffentlichen Gradle-Repositories während der Abhängigkeitsauflösung.

Maintainer-private Dateien, Signierschlüssel, persönliche Testdaten und externe private Ablagen sind keine Build-Eingaben.

## Empfohlene Prüfung

```text
gradlew testDebugUnitTest testReleaseUnitTest
gradlew lintDebug lintRelease
gradlew assembleDebug assembleRelease
```

Der normale `release`-Build kann ohne `ESCA_SIGNING_PROPERTIES` als unsignierter, optimierter Quell-Build erstellt werden. Das ist auch die für F-Droid relevante Build-Grenze; F-Droid baut aus öffentlicher Quelle und signiert die veröffentlichte APK anschließend unabhängig.

Danach sollten Paket, Version, Berechtigungen, Debuggable-Status, ZIP-Struktur und eingebettete Dateien unabhängig geprüft werden. Für eine K2040-Entwicklerveröffentlichung ist zusätzlich die erwartete Signatur separat zu verifizieren.

## Warum noch keine bitgenaue Zusage besteht

Android- und ZIP-Werkzeuge können Zeitstempel, Reihenfolgen, Kompressionsdetails oder Umgebungsmerkmale einbeziehen. Eine belastbare bitgenaue Zusage erfordert mindestens:

- einen vollständig festgelegten Container- oder Build-Umgebungsstand;
- kontrollierte Zeit- und Locale-Einstellungen;
- zwei unabhängige Clean-Builds;
- Vergleich aller APK-Einträge und nicht nur der Gesamtdatei;
- dokumentierte Behandlung der späteren Distributionssignatur.

Bis diese Prüfung abgeschlossen ist, lautet der zulässige Anspruch ausschließlich: gepinnt, selbstständig und aus der veröffentlichten Quelle baubar.
