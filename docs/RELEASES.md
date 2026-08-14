# Veröffentlichungs- und Signaturmodell

## Zwei Signaturvarianten

Esca Agnellis kann über zwei getrennte Signaturketten verteilt werden:

1. **K2040-Entwickler-APK**  
   Von K2040 ausdrücklich als offiziell bezeichnet und mit dem dauerhaften K2040-Entwicklerzertifikat signiert. APKs und Prüfsummen werden über `Kamui2040/K2040-Android-Releases` bereitgestellt.

2. **F-Droid-APK**  
   Von F-Droid unabhängig aus der veröffentlichten Quelle gebaut und mit der F-Droid-Signatur versehen.

Die Open-Source-Lizenzen erlauben unabhängige Builds und Änderungen. Ein modifizierter oder unabhängig signierter Build darf jedoch nicht als offizieller K2040-Build dargestellt werden. `TRADEMARKS.md` kontrolliert diese Abgrenzung.

## Wechsel zwischen Varianten

Beide Varianten verwenden `com.k2040.escaagnellis`, besitzen aber unterschiedliche Zertifikate. Android erlaubt deshalb kein direktes Update von einer Variante auf die andere.

Der unterstützte manuelle Wechsel ist:

1. Hauptsicherung exportieren.
2. Bei aktiviertem Begleiter zusätzlich die getrennte Begleitersicherung exportieren.
3. Die installierte Variante manuell deinstallieren.
4. Die andere Variante installieren.
5. Haupt- und Begleitersicherung jeweils über den passenden Import wiederherstellen.

Keine Projekt-Automatisierung darf die App deinstallieren, App-Daten löschen oder eine Sicherung überschreiben.

## Versionen und Tags

Ein öffentlicher Quell-Tag muss auf den exakt geprüften Quellstand zeigen. Entwickler-APK, Prüfsumme, Quell-Tag und F-Droid-Metadaten müssen dieselbe Version und denselben versionCode beschreiben, auch wenn ihre APK-Signaturen und dadurch ihre Dateihashes verschieden sind.

## Historische Grenze

Version 0.16.0 ist die erste quelloffene Version. Frühere veröffentlichte Binärversionen bis 0.15.0 behalten ihre damaligen proprietären Bedingungen und werden nicht rückwirkend neu lizenziert.

## Getrennte Freigaben

Quell-Repository, Quell-Tag, Entwickler-APK, F-Droid-Einreichung, weitere Stores, Webseiten und Ankündigungen sind getrennte Vorgänge. Die technische Vorbereitung eines Vorgangs autorisiert keinen anderen.
