# Datenschutz / Privacy

## Kurzfassung

Esca Agnellis arbeitet lokal auf dem Android-Gerät. Die App fordert keine Internet-Berechtigung an und verwendet keine Konten, Werbung, Telemetrie, Analyse, Cloud-Synchronisierung, automatische Absturzübertragung oder Hintergrunddatenerfassung.

## Lokal gespeicherte Daten

Die App speichert lokal:

- ausgewählte Tagesportionen und Zusatzportionen;
- Anzeige-, Sprach- und Theme-Einstellungen;
- den optionalen Begleiterstatus, wenn der Begleiter aktiviert wurde.

Der Begleiter ist standardmäßig deaktiviert. Sein Zustand und seine Sicherung bleiben vom Kern-Tracking und dessen Sicherung getrennt.

## Sicherungen und PDF-Berichte

Sicherungen, Wiederherstellungen und PDF-Berichte verwenden Androids Dokumentauswahl. Die App schreibt nur an einen vom Benutzer gewählten Speicherort und liest nur eine vom Benutzer gewählte Datei. Es gibt keinen automatischen Upload und keine verpflichtende Synchronisierung.

## Externe Links

Informations- oder freiwillige Unterstützungslinks werden nur nach einer ausdrücklichen Benutzeraktion an den ausgewählten Browser übergeben. Die App lädt diese Ziele nicht im Hintergrund.

## Berechtigungen

Das veröffentlichte Manifest darf keine Internet-Berechtigung enthalten. Funktionen, die einen Android-Dokumentauswahldialog öffnen, verwenden die vom System bereitgestellten Zugriffsrechte für das jeweils gewählte Dokument.

## Löschen und Wechseln der Signaturvariante

Lokale App-Daten werden durch Android verwaltet. Eine manuelle Deinstallation oder das manuelle Löschen von App-Daten entfernt sie. K2040-Entwickler-APKs und unabhängig signierte F-Droid-APKs können nicht direkt übereinander installiert werden. Vor einem Wechsel müssen die unterstützte Hauptsicherung und bei aktiviertem Begleiter die separate Begleitersicherung exportiert werden.

Kein Projektwerkzeug darf die App automatisch deinstallieren oder App-Daten löschen.

## Keine Gesundheitsdatenübertragung

Die App überträgt keine Ernährungs- oder Nutzungsdaten. Sie ist kein medizinisches Produkt und bietet keine Diagnose, Behandlung oder individualisierte Ernährungsberatung.

## English summary

Esca Agnellis stores tracking, settings and optional companion data locally. It requests no Internet permission and uses no account, advertising, telemetry, analytics, cloud sync, automatic crash reporting or background collection. Backups and PDF reports are handled only through user-selected Android document locations.
