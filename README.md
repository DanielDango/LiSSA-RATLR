LaTeX-Vorlage für Präsentationen
================================

Das vorliegende Paket dient als Vorlage für Präsentationen im [Corporate Design des KIT](https://kit-cd.sts.kit.edu/praesentationen.php) (Fassung von Februar 2025).

Es wird an der Forschungsgruppe [DSiS](https://dsis.kastel.kit.edu) an der KIT-Fakultät für Informatik entwickelt und basiert auf [LaTeX Beamer](https://ctan.org/pkg/beamer).

Autor: [Dr.-Ing. Erik Burger](https://dsis.kastel.kit.edu/staff_erik_burger.php)

Siehe https://sdq.kastel.kit.edu/wiki/Dokumentvorlagen

Hinweise, Verbesserungsvorschläge
=================================

Bitte verwenden Sie das [Issue-Tracking-System von Gitlab](https://gitlab.kit.edu/kit/kastel/sdq/dokumentvorlagen/praesentationen/beamer/-/issues), um auf Probleme mit der Vorlage hinzuweisen oder Erweiterungswünsche zu äußern. Sie können gerne auch eine Änderung per Merge-Request vorschlagen.

Verwendung
==========

Optionen der Dokumentklasse `sdqbeamer`
-----------------------------------------
Die Schriftgröße in der Fußzeile ist standardmäßig größer gewählt, als in der PowerPoint-Vorlage vorgegeben. Diese Vorgabe kann durch die Option `smallfoot` erzwungen werden.

| Schriftgröße Fußzeile | Option               |
| ----------------------| -------------------- |
| etwas größer (12pt)   | `bigfoot` (Standard) |
| KIT-Vorgabe (11pt)    | `smallfoot`          |

Als Sprache sind Deutsch und Englisch verfügbar. Durch die Sprachwahl werden automatisch die passenden Formate (z.B. Anführungszeichen, Datum) gewählt.

| Sprache  | Option          |
| -------- | --------------- |
| Deutsch  | `de` (Standard) |
| Englisch | `en`            |

Die Titel der Folien werden nach der PowerPoint-Vorlage in der Schrift Franklin Gothic gesetzt. Will man für alles Helvetica haben, kann man dies definieren.

| Schriftart Titel  | Option                |
| ----------------- | --------------------- |
| Franklin Gothic   | `franklin` (Standard) |
| Helvetica         | `helvet`              |

Weiterhin kann die Navigationsleiste über die Option `navbaroff` deaktiviert werden.

Mit der Option `kitgrid` kann das Raster der Folien angezeigt werden.

Beispiel: `\documentclass[de,bigfoot]{sdqbeamer}`

Titelfolie
----------
Für die Titelfolie gibt es mehrere Layouts. Sie können durch Frame-Optionen gewählt werden:

| Layout             | Frame-Option             |
| -----------------  | ------------------------ |
| grün/weiß vertikal | `title white vertical`   |
| grün/blau vertikal | `title white vertical`   |
| weiß horizontal    | `title white horizontal` |
| grün horizontal    | `title green horizontal` |

Das Bild auf der Titelfolie kann mit der Option `picture=` gesetzt werden. Bei vertikalen Layouts wird das Bild auf die Höhe eingepasst, bei horizontalen auf die Breite.

Zusätzlich kann bei den horizontalen Layouts die Farbe des KIT-Logos gewählt werden

| Farbe KIT-Logo | Frame-Option             |
| -------------- | ------------------------ |
| bunt           | `kitlogo=rgb`            |
| weiß           | `kitlogo=white`          |
| schwarz        | `kitlogo=black`          |

Beispiel: `\begin{frame}[title white horizontal, picture=images/palladio_bauplan, kitlogo=black]`

Spezialfolien
-------------
Für Inhaltsverzeichnisse kann die Option `tableofcontents=<farbe>` angegeben werden, wobei `farbe` entweder `blue` oder `green` sein kann. Die Inhaltsverzeichnisse werden dann auf einer vollflächig farbigen Folie angezeigt.

Als Trennfolien gibt es Spezial-Layouts mit großflächigen Bildern. Sie können durch Frame-Optionen gewählt werden.

| vertikales Bild im Anteil von: | Frame-Option                   |
| ------------------------------ | ------------------------------ |
| 33 %                           | `picture 33 vertical`          |
| 50 %                           | `picture 50 vertical`          |
| 66 %                           | `picture 66 vertical`          |
| beliebig                       | `picture vertical=<Wert in %>` |

Das Bild wird mit der Option `picture=<pfad>` gesetzt, wobei `pfad` relativ zum Haupdokument engegeben werden muss.

Zusätzlich kann die Farbe des KIT-Logos in der Fußzeile gewählt werden, da es über dem Bild erscheint.

| Farbe KIT-Logo | Frame-Option             |
| -------------- | ------------------------ |
| bunt           | `kitlogo=rgb`            |
| weiß           | `kitlogo=white`          |
| schwarz        | `kitlogo=black`          |

Beispiel: `\begin{frame}[picture 66 vertical,picture=images/palladio_bauplan,kitlogo=black]{Folie mit Bild}`

Logo und Name Abteilung/KIT-Fakultät/Institut
---------------------------------------------

Das Logo rechts unten auf der Titelfolie kann mit dem folgenden Befehl gesetzt werden:

`\grouplogo{mylogo}` (ohne Dateiendung)

Um ein eigenes Logo zu verwenden, bitte die Datei (z.B. `mylogo.pdf`) in das Verzeichnis `logos/` legen und den Befehl anpassen. Falls kein Logo eingefügt werden soll, bitte `\grouplogo{}` setzen.

Der Gruppenname kann mit folgendem Befehl gesetzt werden:

`\groupname{Software Design and Quality}`

Der Gruppenname erscheint in der Fußzeile rechts unten. Lange Namen werden in zwei Zeilen umgebrochen. Falls der Gruppenname leer gelassen wird (`\groupname{}`), wird die volle Breite der Fußzeile für Autornamen und Titel verwendet. 

Die Standardbreite des Gruppennamens sind 89 mm. Sie kann mit 

`\groupnamewidth{120mm}`

verändert werden, wodurch sich auch die Breite des Textfeldes mit Autor und Titel entsprechend ändert. Umbrüche sind mit `\\` möglich. Statt zweizeiliger Fußzeilen empfiehlt sich eventuell die Option `smallfoot`.

LaTeX allgemein
---------------
Siehe https://sdq.kastel.kit.edu/wiki/LaTeX

Dateistruktur
============
`presentation.tex`
------------------
Hauptdatei des LaTeX-Dokuments.

`presentation.bib`
-------------
Beispieldatei für BibTeX-Referenzen
https://sdq.kastel.kit.edu/wiki/BibTeX-Literaturlisten

`sdqbeamer.cls`
-----------------
Dokumentklasse für Präsentationen im KIT-Design.

`logos/`
--------
In diesem Verzeichnis befindet sich das KIT-Logo in verschiedenen Farbvarianten als PDF.

`images/`
--------
In diesem Verzeichnis befindet sich das Hintergrundbild der Titelfolie als JPG.

`CHANGELOG.md`
--------------
Dokumentation der Änderungen in den jeweiligen Versionen.

`README.md`
-----------
Dieser Text.
