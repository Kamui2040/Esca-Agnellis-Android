<p align="center">
  <img src="docs/images/esca-symbol.png" width="120" alt="Esca-Agnellis-Symbol">
</p>

# Esca Agnellis

**Esca Agnellis** ist eine deutschsprachig ausgerichtete Android-App zum lokalen, freundlichen und druckfreien Erfassen von Portionen anhand einer Ernährungspyramide.

[English README](README.en.md)

## Funktionen

- Tagesansicht mit 22 Standardfeldern in sechs visuellen Ebenen
- Übersicht und lokale Statistiken
- Deutsch, Englisch, Spanisch, Französisch und Europäisches Portugiesisch
- System-, Standard- und gemütliche Pastell-Themen
- Lokale Sicherung und Wiederherstellung über Android-Dokumentauswahl
- Lokale PDF-Berichte für frei gewählte Datumsbereiche
- Optionaler Begleiter, standardmäßig deaktiviert und vom Kern-Tracking getrennt
- Keine Werbung, Konten, Telemetrie, Analyse, Cloud-Synchronisierung oder Hintergrunddatenerfassung
- Keine angeforderte Internet-Berechtigung

## Datenschutz

Alle Tracking- und Begleitdaten bleiben lokal auf dem Gerät. Sicherungen und PDF-Berichte werden nur an einen vom Benutzer ausgewählten Android-Dokumentspeicherort geschrieben. Einzelheiten stehen in [PRIVACY.md](PRIVACY.md).

## Bauen und testen

Die öffentliche Quelle ist für unabhängige lokale Builds ausgelegt. Benötigt werden JDK 17, Android SDK 35 und der enthaltene Gradle Wrapper.

```text
Windows:
gradlew.bat testDebugUnitTest lintDebug assembleDebug
gradlew.bat testFdroidUnitTest lintFdroid assembleFdroid

POSIX:
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew testFdroidUnitTest lintFdroid assembleFdroid
```

Weitere Informationen:

- [Build-Anleitung](docs/BUILDING.md)
- [Reproduzierbare und unabhängige Builds](docs/REPRODUCIBLE_BUILDS.md)
- [Veröffentlichungs- und Signaturmodell](docs/RELEASES.md)

## Offizielle und unabhängige Builds

Offizielle K2040-Entwickler-APKs werden über `Kamui2040/K2040-Android-Releases` bereitgestellt. F-Droid baut unabhängig aus öffentlicher Quelle und verwendet eine eigene Signatur.

Beide Varianten verwenden das Paket `com.k2040.escaagnellis`, sind wegen unterschiedlicher Zertifikate aber nicht direkt übereinander installierbar. Vor einem Wechsel müssen die unterstützte Hauptsicherung und bei aktiviertem Begleiter zusätzlich die getrennte Begleitersicherung exportiert werden. Danach erfolgt der Wechsel manuell durch Deinstallation, Installation der anderen Variante und Wiederherstellung. Kein Projektwerkzeug darf die App automatisch deinstallieren oder App-Daten löschen.

## Lizenzierung

- Anwendungscode und Projekt-Buildskripte: GNU GPLv3 (`GPL-3.0-only`) mit einer zusätzlichen K2040-Attributionserhaltungsklausel nach GPLv3 §7(b). Der festgelegte Hinweis `Copyright (c) 2026 K2040.` muss in von K2040 verfasstem Material oder in den Appropriate Legal Notices des enthaltenen Werks erhalten bleiben.
- Freigegebene Dokumentation und Kunstwerke: `CC-BY-4.0`, soweit in den kontrollierenden Dateien angegeben; diese Lizenz verlangt unabhängig davon die erforderliche Namensnennung.
- Drittanbieter-Abhängigkeiten und eingebundene Fremdkomponenten: ihre jeweiligen Lizenzen und Hinweise bleiben maßgeblich und müssen entsprechend erhalten werden.
- Gradle-Wrapper-Komponenten: `Apache-2.0`
- Namen, Logos und Hinweise auf offiziellen Status: [TRADEMARKS.md](TRADEMARKS.md)
- Exakte Herkunft und Attribution der Laufzeit-Kunstwerke: [ASSET_PROVENANCE.yml](ASSET_PROVENANCE.yml); Provenienz der lokalisierten Store-Screenshots: [fastlane/SCREENSHOT_PROVENANCE.md](fastlane/SCREENSHOT_PROVENANCE.md)

Der Standardtext der GNU GPLv3 bleibt unverändert. Die zusätzliche K2040-Klausel steht separat in `LICENSES/GPL-3.0-Section-7b-K2040.txt`. Die übrigen kontrollierenden Texte stehen in `LICENSE`, `LICENSES/`, `NOTICE.md` und `THIRD_PARTY_NOTICES.md`.

## Einordnung

Esca Agnellis ist kein offizielles Produkt von BZfE oder BLE und wird von diesen Stellen weder unterstützt noch bestätigt. Die App bietet allgemeine Erfassungs- und Orientierungsfunktionen, aber keine medizinische, diätetische oder individualisierte Ernährungsberatung.

## Mitwirken und Hilfe

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [SECURITY.md](SECURITY.md)
- [SUPPORT.md](SUPPORT.md)
- [CHANGELOG.md](CHANGELOG.md)
