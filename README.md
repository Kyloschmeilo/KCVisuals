# KC Visuals

KC Visuals ist eine private, rein clientseitige Fabric-Mod für Minecraft 1.21.11. Sie setzt aus bis zu 82 PvP-Resource-Packs ein eigenes Pack zusammen (pro Kategorie ein Pack deiner Wahl, z. B. Schwerter von Pack A, Totem von Pack B, Endkristall von Pack C), bringt dazu eine Reihe optischer Module und ein verschiebbares HUD mit.

## Installation

1. Fabric Loader 0.20.1 oder neuer sowie Fabric API 0.141.6+1.21.11 installieren. Mod Menu ist optional.
2. `kcvisuals-1.0.0.jar` in den Ordner `.minecraft\mods` legen.
3. Packs bereitstellen, auf einem der beiden Wege:
   - `install-packs.ps1` ausführen: kopiert alle Zips aus `packs\local` und `packs\modrinth` nach `.minecraft\kcvisuals\packs`. Mit `-Target <Ordner>` lässt sich ein anderer `.minecraft`-Ordner angeben. Falls PowerShell Skripte sperrt: `powershell -ExecutionPolicy Bypass -File install-packs.ps1`.
   - Eigene Pack-Zips (oder entpackte Pack-Ordner) einfach in `.minecraft\kcvisuals\packs` ablegen. Über „Packs neu einlesen“ im Menü werden sie erkannt.

## Benutzung

Das Menü öffnest du mit `/kcv` oder über Mod Menu. Es hat vier Reiter:

- **Packs**: links die Kategorien, rechts die Packs, die diese Kategorie enthalten (mit Vorschau). Pro Kategorie ein Pack wählen oder „Vanilla“. Mit **Anwenden** wird das Pack gebaut, aktiviert und die Ressourcen werden neu geladen. „Zurücksetzen“ verwirft ungespeicherte Änderungen.
- **Visuals**: alle Module ein- und ausschalten und einstellen.
- **HUD**: HUD-Elemente ein- und ausschalten und einstellen; der HUD-Editor erlaubt das Verschieben per Maus.
- **Profile**: Profile anlegen, duplizieren, umbenennen, aktivieren und löschen. Das aktive Profil wird beim Start automatisch angewendet.

## Visual-Module

- Sprungkreise: leuchtende Ringe an den Füßen beim Springen und Landen.
- Flugbahnen: Bahn und Aufprallpunkt des Wurfgegenstands in der Hand.
- China-Hut: durchscheinender Kegel über dem Kopf (dritte Person).
- Todesmarker: markiert den letzten Todespunkt.
- Trefferfarbe: färbt getroffene Wesen in deiner Farbe.
- Schadenszahlen: schwebende Zahlen über getroffenen Wesen.
- Trefferpartikel: Herzen, Sterne oder Funken am Ziel.
- Hitbox-Stil: Farbe und Linienbreite der F3+B-Hitboxen.
- Eigene Hand: Position, Größe, Drehung und Schlagtempo der Hand.
- Eigener Himmel: Himmelsfarben, Sterne, Nebel und feste Uhrzeit.
- Fullbright: volle Helligkeit ohne Gamma-Änderung.
- Fadenkreuz: Kreuz, Punkt, Kreis oder Plus-Punkt.
- Seitenverhältnis: horizontal gestrecktes Bild.
- Render-Tweaks: Feuer, Totem, Kürbis, Wackeln bei Schaden und Explosionspartikel.

## HUD-Elemente

- Wasserzeichen: Name, FPS, Ping und Server.
- Statuseffekte: Effekte mit Symbol und Restzeit.
- Ziel-HUD: zuletzt angegriffenes Ziel (8 s).
- Tastenanzeige: WASD, Maustasten und CPS.
- Rüstung & Totems: Totem-Anzahl und Haltbarkeit der Rüstung.
- Koordinaten: XYZ und Blickrichtung.

## Wo liegen die Daten?

Alles liegt im `.minecraft`-Ordner:

- `kcvisuals\packs`: deine Pack-Zips bzw. -Ordner.
- `kcvisuals\generated`: das erzeugte Pack `KC Visuals Custom` (wird bei jedem Anwenden neu gebaut, nicht von Hand bearbeiten).
- `kcvisuals\profiles`: gespeicherte Profile (eine JSON-Datei je Profil) und das aktive Profil.
- `config\kcvisuals.json`: Module, Einstellungen und HUD-Layout.

## Hinweise

- Alle Visuals sind rein clientseitig und nur kosmetisch. Sie verändern nichts am Server und geben keinen spielerischen Vorteil durch Spiellogik; ob ein Server sie dennoch nicht möchte, entscheidest du selbst.
- Die Packs gehören ihren jeweiligen Autoren und sind nur für den privaten Gebrauch gedacht. Quellen und Autoren stehen in `packs\SOURCES.md`. Die Packs selbst sind nicht Teil des Repositorys.
