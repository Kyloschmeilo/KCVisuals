# KC Visuals – Design

Stand: 2026-11-15 · Minecraft 1.21.11 · Fabric · nur privat

## Ziel und Warum

KC Visuals ist eine private Fabric-Mod für Crystal-/PvP-Spieler. Kern ist ein
**Ressourcenpaket-Manager**: Für jede PvP-relevante Textur (Schwert, Rüstung,
Kristall, Totem, Perle …) wählt man aus einer großen Sammlung von Packs die
Variante, die einem gefällt, und baut sich so sein eigenes Pack zusammen,
ohne Dateien von Hand zu mergen. Daneben gibt es einen **Visuals-Overhaul** im Stil
des Referenzvideos und von Pulse Visuals (pulsevisuals.pro): Sprungkreise,
Flugbahn, Hit-Color, Schadenszahlen, eigener Himmel, HUD.

## Ziele

1. Pack-Manager mit Kategorien, Vorschau, Profilen, Live-Anwendung.
2. Pack-Sammlung: die 32 vorhandenen Packs (`Downloads/#/txt.pack`) **plus 50
   zusätzliche**, beliebte PvP-Packs für 1.21.x.
3. Visuals-Module mit Farbe/Größe/An-Aus pro Modul.
4. HUD-Elemente, frei verschiebbar.
5. Ein Menü, das per `/kcv` oder über Mod Menu geöffnet wird.

## Nicht-Ziele / Anti-Ziele

- **Kein fester Hotkey** für das Menü (ausdrücklich gewünscht).
- **Keine unfairen Vorteile**: kein AutoFish, keine Tracer/ESP durch Wände, kein
  Hitbox-Vergrößern, das Treffer beeinflusst. Alles ist rein optisch und client-seitig.
  Die Mod soll auf Servern nicht zu einem Bann führen.
- Kein Veröffentlichen (wegen der Rechte an den Packs), kein Marktplatz, keine
  Accounts und keine Kosmetik-Server wie bei Pulse.
- `coreclient` wird nicht verändert.

## Rahmenbedingungen

- Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Yarn
  1.21.11+build.6, Loom 1.20.0-alpha.27, Java 21 (übernommen aus `coreclient`).
- Mod Menu als optionale Abhängigkeit (nur für den Config-Button).
- Packs: zusammen grob 0,5–1 GB. Sie liegen **nicht im Jar**, sondern in
  `.minecraft/kcvisuals/packs/`.
- Pack-Formate für 1.21.x (pack_format 34–6x); ältere Packs werden gelesen,
  Texturpfade, die sich seit 1.13/1.20.5 geändert haben, werden wo möglich gemappt.

## Ansatz (gewählt: A)

| | A) Generiertes Pack (**gewählt**) | B) Textur-Austausch per Mixin |
|---|---|---|
| Funktionsweise | Ausgewählte Dateien werden in ein Pack „KC Visuals Custom" kopiert, das ganz oben aktiviert wird, dann Texturen neu laden | Mixin in den TextureManager/ResourceManager ersetzt einzelne Texturen im Speicher |
| Animationen, Item-Modelle, Partikel | funktionieren automatisch | müssen einzeln nachgebaut werden |
| Robustheit bei Updates | hoch | gering |
| Nachteil | ein zusätzlicher Eintrag in der Pack-Liste | viel Mixin-Code, fehleranfällig |

Gewählt wurde A, weil damit alles, was Packs können, ohne Sonderfälle funktioniert.

## Aufbau

### 1. Pack-Manager

**Pack-Index**: Beim Start (und per „Neu einlesen") werden alle Zips/Ordner in
`kcvisuals/packs/` gescannt. Pro Pack wird festgehalten, welche **Kategorien** es
abdeckt. Die Ergebnisse werden in `kcvisuals/index.json` gecacht (Schlüssel: Dateiname,
Größe, Änderungsdatum).

**Kategorien**: Jede Kategorie ist eine Liste von Pfaden (Textur + zugehöriges
`.mcmeta`, Item-Modell unter `models/item`, Item-Definition unter `items/`). Die
Kategorien stehen in einer JSON-Datei in der Mod, damit man sie leicht erweitern kann.
Startumfang:

- Waffen: Schwerter (alle Materialien), Äxte, Streitkolben (Mace), Bogen, Armbrust, Dreizack, Pfeile
- Rüstung: Netherite/Diamant (Item-Icons + angezogene Layer), Elytra, Schild
- Crystal-PvP: End-Kristall (Entity + Item), Obsidian, Respawn Anchor, Glowstone
- Totem, Ender-Perle, Goldene Äpfel, Windladung
- Tränke/Wurftränke, Splash-Partikel
- Blöcke: Cobweb, Lava/Wasser, Wolle/Beton (optional)
- Effekte: Feuer (Overlay + Höhe), Explosions-/Kristall-Partikel, Crit-Partikel
- GUI: Hotbar/Widgets, Herzen/Hunger/Rüstungsleiste, Crosshair, Inventar-Hintergründe
- Himmel/Umgebung: Sonne, Mond, Sterne (falls das Pack welche hat)

**Auswahl & Bau**: Pro Kategorie wählt man „Vanilla" oder ein Pack. Beim Anwenden
werden die Dateien in `kcvisuals/generated/KC Visuals Custom/` geschrieben (Ordner,
kein Zip, damit es schneller geht). Danach wird das Pack an die Spitze der aktiven
Liste gesetzt und `MinecraftClient#reloadResources()` aufgerufen.

**Vorschau**: Zur Laufzeit wird ein Thumbnail aus der Haupttextur der Kategorie
geladen (NativeImage → DynamicTexture). Bei Rüstung und Kristall wird zusätzlich
eine kleine 3D-Vorschau auf einem Dummy-Player bzw. Kristall gerendert (siehe
Builder-Spielraum).

**Profile**: Eine Auswahl wird als Profil in `kcvisuals/profiles/*.json` gespeichert
und kann geladen und dupliziert werden.

**Pack-Beschaffung (einmalig, während der Entwicklung)**: Die 32 vorhandenen Packs
werden in den Pack-Ordner kopiert. Für die 50 zusätzlichen Packs wird recherchiert
(Modrinth, PlanetMinecraft, CurseForge, YouTube-Creator-Packs; Kriterium: Downloads/
Beliebtheit, 1.21-kompatibel, Duplikate zu den 32 vorhandenen vermeiden). **Vor dem
Download wird die Liste mit Quelle und Größe dem Nutzer zur Freigabe gezeigt.**

### 2. Visuals-Module

Jedes Modul hat An/Aus und eigene Einstellungen (Farbe mit Alpha, Größe, Dauer …).

| Modul | Beschreibung |
|---|---|
| Jump Circles | Farbiger, auslaufender Ring beim Absprung/Landen |
| Trajectories | Flugbahn-Bogen für Perle, Bogen, Dreizack, Tränke, Windladung (nur die eigene Hand) |
| Hit Color | Farbe des Rot-Blitzes beim Treffer ändern |
| Damage Numbers | Schwebende Schadenszahlen an getroffenen Entities |
| Hit Particles | Eigene Partikel beim Treffer (Herzen, Sterne, Funken), mit Farbe und Anzahl |
| Custom Hand | Position, Größe und Rotation des gehaltenen Items, Schwing-Geschwindigkeit |
| Custom Sky | Himmelsfarbe/Sternenhimmel, Nebel aus/an, feste Uhrzeit (nur optisch) |
| Fullbright | Gamma-Override |
| Crosshair | Eigenes Fadenkreuz (Form, Farbe, Größe, Dynamik) |
| China Hat | Kegel über dem eigenen Kopf (Third Person), Farbe/Verlauf |
| Hitbox Style | Farbe/Dicke der F3+B-Hitboxen (nur Optik, keine Größenänderung) |
| Render Tweaks | Feuer-Overlay niedriger/aus, Totem-Animation kleiner, Pumpkin-Overlay aus, Hurt-Cam aus, Kristall-Explosions-Partikel reduzieren |
| Aspect Ratio | Gestrecktes Bild (Stretched Res) |
| Death Marker | Markierung am letzten eigenen Todespunkt mit Entfernung |

### 3. HUD

Watermark-Leiste (Name, Version, FPS, Ping, Server), Trank-Effekte mit Restzeit,
Target-HUD (Name, HP, Rüstung des zuletzt getroffenen Spielers), Keystrokes,
Rüstungs-/Totem-Zähler, Koordinaten. Ein HUD-Editor erlaubt das Verschieben per
Drag & Drop. Positionen werden relativ gespeichert, damit sie auch bei anderer
Auflösung passen.

### 4. Menü & Bedienung

- Öffnen: **`/kcv`** (Client-Command via Fabric API) oder **Mod Menu → KC Visuals**.
  Kein Keybind.
- Ein Menü mit Tabs: **Packs** · **Visuals** · **HUD** · **Profile**.
- Optik: dunkler, halbtransparenter Hintergrund, abgerundete Panels, lila
  Akzentfarbe (anpassbar), Animationen beim Öffnen. Stil angelehnt an moderne
  PvP-Clients, eigenes Design.
- Packs-Tab: links die Kategorienliste, in der Mitte ein Raster aus Vorschaukacheln
  aller Packs, die diese Kategorie haben, Suchfeld. Oben „Anwenden" und „Zurücksetzen".

### 5. Speicherung

`.minecraft/config/kcvisuals.json` (Module, HUD), `.minecraft/kcvisuals/`
(packs, generated, profiles, index.json).

## Vom Nutzer entschieden

- Weg A (generiertes Pack).
- Nur privat. Packs dürfen mitgeliefert werden.
- 32 vorhandene Packs plus 50 zusätzliche.
- Menü per `/kcv` oder Mod Menu, keine feste Taste.

## Vom Builder entschieden (Claude) – zur Prüfung

- Packs liegen außerhalb des Jars in `kcvisuals/packs/` (Größe, einfach erweiterbar).
- Neues eigenständiges Projekt `Desktop/mods/KCVisuals`, Setup aus `coreclient` übernommen.
- AutoFish, Tracer und ESP werden bewusst nicht gebaut.
- Modulliste oben = Video + Pulse-Features minus unfaire Features.

## Spielraum für den Builder

- Genaue Pfadliste je Kategorie (aus den 32 Packs ableiten).
- 3D-Vorschau: zuerst 2D-Thumbnails, 3D später, falls sinnvoll.
- Render-Details der Module (Mixins vs. Fabric-Rendering-Events in 1.21.11).
- Reihenfolge: Grundgerüst + Menü → Pack-Manager → Pack-Beschaffung → Visuals → HUD.
