# Mitwirken / Contributing

Beiträge zu Esca Agnellis sind willkommen, wenn sie die lokale, zugängliche und druckfreie Produktausrichtung erhalten.

## Entwicklungsgrundsätze

Änderungen müssen insbesondere erhalten:

- deutschsprachige Standard- und Fallback-Texte sowie alle unterstützten Übersetzungen;
- schmale Bildschirmdarstellung, Touch- und Scroll-Verhalten und Barrierefreiheit;
- lokale Haupt- und Begleiterspeicherung mit getrennten Sicherungsformaten;
- vollständige Validierung vor Schreibvorgängen und unveränderte unterstützte Daten bei Fehlern;
- keine Internet-Berechtigung, Konten, Werbung, Telemetrie, Analyse, Cloud-Pflicht oder Hintergrunddatenerfassung;
- einen optionalen, standardmäßig deaktivierten und nicht strafenden Begleiter;
- lokale PDF- und Dokumentauswahl-Funktionen.

## Entwicklungsumgebung

Verwende JDK 17, Android SDK 35 und den enthaltenen Gradle Wrapper. Siehe [docs/BUILDING.md](docs/BUILDING.md).

Vor einem Pull Request sollten für runtime-relevante Änderungen die passenden Aufgaben erfolgreich sein, typischerweise:

```text
testDebugUnitTest
testReleaseUnitTest
lintDebug
lintRelease
assembleDebug
assembleRelease
```

Der normale `release`-Build muss ohne private Signierkonfiguration baubar bleiben und ist dann unsigned; diese Build-Grenze wird auch für F-Droid verwendet. Führe außerdem die Repository-Prüfungen für Lokalisierung, F-Droid-Härtung, Lizenzen und Kunstwerk-Herkunft aus, soweit sie vom Änderungstyp betroffen sind. Reine Dokumentationsänderungen benötigen keinen Android-Build, wenn Laufzeit, Ressourcen und Build-Konfiguration unverändert bleiben.

## Übersetzungen

Deutsch ist die kontrollierende Standardsprache. Änderungen an Benutzertexten müssen auf Vollständigkeit und Bedeutung in Deutsch, Englisch, Spanisch, Französisch und Europäischem Portugiesisch geprüft werden. Unübersetzte Platzhalter, beschädigte Zeichen und stilles Zurückfallen auf falsche Texte gelten als Fehler.

## Datenschutz und Testdaten

Committe keine echten Sicherungen, Ernährungsdaten, Gerätekennungen, privaten Pfade, Zugangsdaten, Schlüssel, Zertifikate, Build-Ausgaben, Absturzprotokolle oder Screenshots mit persönlichen Informationen. Tests verwenden ausschließlich kleine synthetische Daten.

## Kunstwerke und andere Assets

Jedes visuelle Asset benötigt einen exakten Eintrag in `ASSET_PROVENANCE.yml` mit Pfad, SHA-256, Größe, Format, Rechten, Lizenz, Attribution und Blockerstatus. Unklare Rechte oder ein Hash-Mismatch blockieren die Aufnahme.

## Developer Certificate of Origin

Jeder Commit muss eine gültige `Signed-off-by`-Zeile enthalten.

Der Commit muss den von Git erzeugten `Signed-off-by`-Trailer mit dem echten Namen und der gültigen E-Mail-Adresse der beitragenden Person enthalten.

Mit dieser Zeile bestätigt die beitragende Person die Bedingungen des Developer Certificate of Origin 1.1. Verwende zum Erstellen eines signierten Commits beispielsweise `git commit -s`.

## Lizenz des Beitrags

Mit einem Beitrag wird zugestimmt, dass er unter der für den jeweiligen Bereich angegebenen Projektlizenz veröffentlicht werden kann. Anwendungscode und Projekt-Buildskripte verwenden `GPL-3.0-only`; ausdrücklich freigegebene Dokumentation und Kunstwerke können `CC-BY-4.0` verwenden.

## Pull Requests

Ein Pull Request sollte Zweck, betroffene Bereiche, lokale Validierung und noch offene manuelle Prüfungen beschreiben. Große unabhängige Änderungen sollten getrennt bleiben. Änderungen dürfen keine automatische Deinstallation, Datenlöschung oder Veröffentlichung auslösen.
