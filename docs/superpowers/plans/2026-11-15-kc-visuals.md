# KC Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Private Fabric-1.21.11 mod "KC Visuals": a per-category PvP resource-pack composer plus purely visual overhaul modules and a movable HUD, opened via `/kcv` or Mod Menu.

**Architecture:** Pure-Java core (pack index, category matching, pack builder, profiles, config) with no Minecraft imports, so it is JUnit-testable; a thin Minecraft layer (screens, mixins, renderers) on top. The composer writes a folder pack `KC Visuals Custom` into `.minecraft/kcvisuals/generated/`, registers that directory as a pack source and reloads resources (design approach A).

**Tech Stack:** Java 21, Fabric Loom 1.20.0-alpha.27, Minecraft 1.21.11, Yarn 1.21.11+build.6, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Mod Menu (optional, `modCompileOnly` + `modLocalRuntime`), Gson (bundled with MC), JUnit 5.

**Spec:** `C:\Users\petra\Desktop\mods\KCVisuals\docs\2026-11-15-kc-visuals-design.md`

## Global Constraints

- Project root: `C:\Users\petra\Desktop\mods\KCVisuals`; package `com.kcvisuals`; mod id `kcvisuals`; name "KC Visuals".
- Build setup copied from `C:\Users\petra\Desktop\mods\coreclient` (gradle wrapper 9.9.1, versions above). `coreclient` is never modified, and no code from its combat/crystalpvp/macepvp/ESP modules is reused.
- `environment: "client"`. No keybind opens the menu; only `/kcv` and Mod Menu.
- Nothing may give gameplay advantage: no AutoFish, tracers, ESP, through-wall info, or hitbox size changes. Hitbox module = colour/line width only.
- Packs never go into the jar. Paths: packs `<gameDir>/kcvisuals/packs/`, generated `<gameDir>/kcvisuals/generated/KC Visuals Custom/`, profiles `<gameDir>/kcvisuals/profiles/*.json`, index `<gameDir>/kcvisuals/index.json`, module/HUD config `<gameDir>/config/kcvisuals.json`.
- Default accent colour `0xFF9B5CFF` (purple), user-changeable.
- Downloading the 50 additional packs requires showing the user a list (name, source URL, size) and getting approval first.
- UI text in German.

## Review Focus

1. **Broken/odd packs** (corrupt zip, zip with a single top-level folder wrapping `assets/`, missing `pack.mcmeta`, `§` colour codes and Unicode in file names): the scan must skip or unwrap them and never crash the game. Tested in Task 2.
2. **Pre-1.13/pre-1.21 texture paths** (`textures/items/`, `textures/blocks/`, `diamond_sword.png` vs. old names): the matcher maps legacy folders so old packs still show up. Tested in Task 2.
3. **Re-applying a selection** after removing an option (e.g. Totem back to "Vanilla"): files from the previous build must not survive in the generated pack. Tested in Task 3.
4. **A selected pack being deleted from the folder** while a profile still references it: loading the profile falls back to Vanilla for that category and reports it, no crash. Tested in Task 4.
5. **HUD positions after resolution/GUI-scale change**: elements stay on screen (clamped). Tested in Task 8.

---

### Task 1: Project skeleton, `/kcv`, Mod Menu, empty menu screen

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (copied from coreclient), `.gitignore`
- Create: `src/main/resources/fabric.mod.json`, `src/main/resources/kcvisuals.mixins.json`, `src/main/resources/assets/kcvisuals/icon.png`
- Create: `src/main/java/com/kcvisuals/KCVisuals.java`, `src/main/java/com/kcvisuals/KCPaths.java`, `src/main/java/com/kcvisuals/compat/ModMenuIntegration.java`, `src/main/java/com/kcvisuals/gui/KCScreen.java`, `src/main/java/com/kcvisuals/gui/Theme.java`
- Test: `src/test/java/com/kcvisuals/KCPathsTest.java`

**Interfaces:**
- Produces: `KCPaths.of(Path gameDir)` → record with `packs()`, `generated()`, `generatedPack()`, `profiles()`, `index()`, `config()`; `ensureDirs()` creates them. `KCVisuals.openMenu()` schedules `new KCScreen(parent)` on the next client tick (needed because chat closes the screen after a command). `KCScreen(Screen parent)` with tab enum `Tab { PACKS, VISUALS, HUD, PROFILES }` and `registerTab(Tab, TabPanel)`; `interface TabPanel { void init(int x,int y,int w,int h); void render(DrawContext, int mx,int my,float delta); boolean mouseClicked(double,double,int); … }`. `Theme.ACCENT` (mutable int, default `0xFF9B5CFF`), `Theme.BG = 0xC0101018`, `Theme.PANEL = 0xE0181824`, `Theme.TEXT = 0xFFEDEDF5`.

- [ ] **Step 1:** Copy wrapper/gradle files from coreclient; `build.gradle` = coreclient's plus `archivesName = "kcvisuals"`, group `com.kcvisuals`, Mod Menu maven `https://maven.terraformersmc.com/releases/` with the Mod Menu version for 1.21.11 (look it up on modrinth.com/mod/modmenu/versions), `testImplementation "org.junit.jupiter:junit-jupiter:5.11.3"`, `test { useJUnitPlatform() }`. `git init`, initial commit.
- [ ] **Step 2: Failing test** `KCPathsTest.createsAllDirs`: with `@TempDir`, `KCPaths.of(tmp).ensureDirs()` → `packs`, `generated/KC Visuals Custom`, `profiles` exist; `config()` equals `tmp/config/kcvisuals.json`.
- [ ] **Step 3:** Run `./gradlew test` → FAIL (class missing).
- [ ] **Step 4:** Implement `KCPaths`; `KCVisuals` client entrypoint calls `ensureDirs()` with `FabricLoader.getInstance().getGameDir()` and registers `ClientCommandRegistrationCallback` → literal `kcv` → `openMenu()`. `ModMenuIntegration implements ModMenuApi` returns `KCScreen::new`. `KCScreen` draws the background, title "KC Visuals", 4 tabs (Packs, Visuals, HUD, Profile) with accent underline and a 150 ms open fade.
- [ ] **Step 5:** `./gradlew test` → PASS; `./gradlew runClient`, type `/kcv` in a singleplayer world → menu opens; Mod Menu → KC Visuals → gear opens the same screen.
- [ ] **Step 6:** Commit `feat: skeleton, /kcv and Mod Menu entry`.

### Task 2: Pack scanner and category matcher (pure Java)

**Files:**
- Create: `src/main/java/com/kcvisuals/pack/PackSource.java`, `PackScanner.java`, `Category.java`, `CategoryRegistry.java`, `LegacyPaths.java`, `PackIndex.java`
- Create: `src/main/resources/assets/kcvisuals/categories.json`
- Test: `src/test/java/com/kcvisuals/pack/PackScannerTest.java`, `CategoryRegistryTest.java`, test zips built in-test via `ZipOutputStream`

**Interfaces:**
- Produces:
  - `record Category(String id, String group, String displayName, String previewPath, List<String> paths)`; paths are relative to `assets/minecraft/` and may end in `*` (prefix glob, e.g. `textures/entity/end_crystal/*`).
  - `CategoryRegistry.load(InputStream json)`; `List<Category> all()`; `Category get(String id)`.
  - `interface PackSource extends Closeable { String id(); String displayName(); Set<String> entries(); InputStream open(String entryPath) throws IOException; }` with `PackSource.open(Path zipOrDir)`; entries are normalised to start with `assets/…`. If a zip has exactly one top-level folder containing `assets/`, that prefix is stripped. `displayName` strips `§x` codes and `.zip`.
  - `PackScanner.scan(Path packsDir, CategoryRegistry reg)` → `PackIndex` = `Map<String packId, PackInfo>`; `record PackInfo(String id, String displayName, Path file, long size, long mtime, Set<String> categories)`. Corrupt files are logged and skipped. Cached to/from `index.json` by `(file name, size, mtime)`.
  - `LegacyPaths.normalise(String path)` maps `textures/items/` → `textures/item/`, `textures/blocks/` → `textures/block/` plus a small rename table (e.g. `gold_sword` → `golden_sword`, `ender_pearl` stays). Used when matching **and** when building (Task 3 writes the modern path).
- [ ] **Step 1:** Write `categories.json`. Derive the path list for every design category by listing the contents of the 32 packs in `C:\Users\petra\Downloads\#\txt.pack` (`unzip -l`) and vanilla 1.21.11 asset names. Required ids: `swords, axes, mace, bow, crossbow, trident, arrows, armor_netherite, armor_diamond, armor_layers, elytra, shield, end_crystal, obsidian, respawn_anchor, glowstone, totem, ender_pearl, golden_apple, wind_charge, potions, splash_particles, cobweb, water_lava, fire, explosion_particles, crit_particles, hotbar, hud_icons, crosshair, inventory_gui, sky`. Each category also includes the matching `models/item/…`, `items/…json` and `.mcmeta` siblings.
- [ ] **Step 2: Failing tests:**
  - `scanFindsCategories`: zip with `assets/minecraft/textures/item/totem_of_undying.png` → `categories == {"totem"}`.
  - `nestedFolderZipIsUnwrapped`: same file under `MyPack/assets/…` → still `{"totem"}`.
  - `corruptZipIsSkipped`: random bytes named `bad.zip` → index has no entry, no exception.
  - `legacyItemsFolderMatches`: `textures/items/diamond_sword.png` → contains `"swords"`.
  - `colourCodesStripped`: file `§5§lAraxy ᴠ₁.₂.zip` → displayName `"Araxy ᴠ₁.₂"`.
  - `indexCacheReused`: second scan with unchanged file does not reopen the zip (count opens through a package-private hook).
- [ ] **Step 3:** `./gradlew test` → FAIL.
- [ ] **Step 4:** Implement with `java.util.zip.ZipFile` (UTF-8, falling back to CP437 for names) and Gson.
- [ ] **Step 5:** `./gradlew test` → PASS. Also run a throwaway main/test against the real 32 packs and print category coverage. Every pack must hit at least one category; investigate any that hit none.
- [ ] **Step 6:** Commit `feat: pack scanner and category matching`.

### Task 3: Pack builder and resource registration

**Files:**
- Create: `src/main/java/com/kcvisuals/pack/Selection.java`, `PackBuilder.java`, `src/main/java/com/kcvisuals/pack/GeneratedPackProvider.java`, `src/main/java/com/kcvisuals/mixin/ResourcePackManagerMixin.java` (or Fabric API pack hook if available in 0.141.6), `src/main/java/com/kcvisuals/pack/PackApplier.java`
- Test: `src/test/java/com/kcvisuals/pack/PackBuilderTest.java`

**Interfaces:**
- Consumes: `PackIndex`, `PackSource.open`, `CategoryRegistry`, `LegacyPaths.normalise`.
- Produces:
  - `Selection`: `Map<String categoryId, String packId>`; missing key = Vanilla.
  - `PackBuilder.build(Selection, PackIndex, CategoryRegistry, Path outDir)` → `BuildReport(int filesWritten, List<String> missingPacks)`. Deletes `outDir/assets` first, writes `pack.mcmeta` (`pack_format` = 1.21.11's resource pack format, `description` "KC Visuals Custom"), copies every entry of every selected category under its normalised modern path. If a pack is missing, it goes into `missingPacks` and is skipped.
  - `PackApplier.apply(Selection)` (MC side): runs `build` off-thread, then on the client thread ensures pack id `kcvisuals:custom` is enabled **on top** of `options.resourcePacks`, then `client.reloadResources()`. The pack is registered as a folder pack via the pack-provider hook (`GeneratedPackProvider`).
- [ ] **Step 1: Failing tests:**
  - `buildCopiesOnlySelectedCategory`: pack A has totem + sword; selection `{totem: A}` → output contains the totem png, no sword png, plus `pack.mcmeta`.
  - `rebuildRemovesDeselected`: build `{totem: A}`, then build `{}` → no totem file left.
  - `legacyPathWrittenModern`: source `textures/items/totem_of_undying.png` → written as `textures/item/totem_of_undying.png`.
  - `missingPackReported`: selection points to unknown id → `missingPacks == ["x"]`, no exception.
- [ ] **Step 2:** `./gradlew test` → FAIL. **Step 3:** Implement `PackBuilder`. **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** Implement provider + `PackApplier`. Copy one real pack into the dev run's `run/kcvisuals/packs/`, call apply with `{totem: thatPack}` from a temporary debug command, `runClient` → totem in the inventory shows the pack's texture, "KC Visuals Custom" is on top of the resource pack list. Remove the debug command.
- [ ] **Step 6:** Commit `feat: build and apply generated pack`.

### Task 4: Profiles and selection persistence

**Files:**
- Create: `src/main/java/com/kcvisuals/pack/ProfileStore.java`
- Test: `src/test/java/com/kcvisuals/pack/ProfileStoreTest.java`

**Interfaces:**
- Produces: `ProfileStore(Path dir)`; `List<String> list()`; `Selection load(String name, PackIndex idx)` → `LoadResult(Selection selection, List<String> droppedCategories)`; `save(String name, Selection)`; `duplicate(String from, String to)`; `delete(String)`; `String active()` / `setActive(String)` (stored in `profiles/_active.txt`). Default profile name `"Standard"`.
- [ ] **Step 1: Failing tests:** `saveLoadRoundTrip`; `loadDropsMissingPack` (profile references a pack not in the index → category absent, listed in `droppedCategories`); `invalidNameRejected` (names with `/ \ : * ? " < > |` throw `IllegalArgumentException`); `activeDefaultsToStandard`.
- [ ] **Step 2:** FAIL → **Step 3:** implement (Gson, one JSON per profile) → **Step 4:** PASS.
- [ ] **Step 5:** On client start: load the active profile and, if it is non-empty, call `PackApplier.apply` once.
- [ ] **Step 6:** Commit `feat: profiles`.

### Task 5: Packs tab and Profiles tab UI

**Files:**
- Create: `src/main/java/com/kcvisuals/gui/packs/PacksTab.java`, `CategoryList.java`, `PackGrid.java`, `PreviewCache.java`, `src/main/java/com/kcvisuals/gui/profiles/ProfilesTab.java`, `src/main/java/com/kcvisuals/gui/widget/*` (Button, TextField, ScrollPanel, ColorPicker, Slider, Toggle – shared with Tasks 6–8)

**Interfaces:**
- Consumes: `PackIndex`, `CategoryRegistry`, `ProfileStore`, `PackApplier.apply`, `KCScreen.registerTab`.
- Produces: `PreviewCache.get(String packId, Category c)` → `Identifier` of a `NativeImageBackedTexture` created from `c.previewPath()` (first frame only for animated textures, scaled with nearest filtering); cache evicted on menu close. Widgets in `gui/widget` with the `Theme` colours, reused by later tabs.
- [ ] **Step 1:** Packs tab layout: left = categories grouped by `group` with a count of available packs; centre = grid of 48×48 tiles (pack name below, Vanilla tile first, selected tile with an accent border); top = search field (filters pack names), "Anwenden", "Zurücksetzen", "Packs neu einlesen". Clicking a tile changes the in-memory selection only; "Anwenden" saves to the active profile and calls `PackApplier.apply`, showing a toast "Pack wird angewendet…" → "Fertig (n Dateien)" or the missing packs.
- [ ] **Step 2:** Profiles tab: list, New, Duplicate, Rename, Delete (with confirmation), Activate (= load + apply). Dropped categories from `LoadResult` are shown as a warning toast.
- [ ] **Step 3: Verify** with `runClient` and at least 3 real packs: select the sword from pack 1, totem from pack 2, crystal from pack 3, apply → all three are visible in-game at the same time; switch profile → textures change; the menu stays responsive during apply (build runs off-thread).
- [ ] **Step 4:** Commit `feat: packs and profiles UI`.

### Task 6: Pack collection (32 local + 50 additional)

**Files:**
- Create: `C:\Users\petra\Desktop\mods\KCVisuals\packs\SOURCES.md` (name, author, source URL, size, MC version for each pack)
- Create: `C:\Users\petra\Desktop\mods\KCVisuals\install-packs.ps1` (copies `packs\*` into a given `.minecraft\kcvisuals\packs`)

- [ ] **Step 1:** Research the 50 most popular PvP/Crystal-PvP packs for 1.21.x not already among the 32 (dedupe by name/author). Sources: Modrinth (sort by downloads, tag pvp), PlanetMinecraft, CurseForge, well-known creator packs. Rank by downloads/popularity; prefer direct downloads without ad-gates.
- [ ] **Step 2:** Show the user the list (name, source, size). **Stop until the user approves.**
- [ ] **Step 3:** Download the approved packs into `KCVisuals\packs\` (each a separate, untrusted zip; only zips are kept, nothing is executed). Copy the 32 local packs (including `Pinke packs\*`) there too.
- [ ] **Step 4:** Run the Task 2 coverage check over all ~82 packs; drop or note any pack that matches no category or fails to open.
- [ ] **Step 5:** `install-packs.ps1 -Target "$env:APPDATA\.minecraft"` and verify that all of them appear in the Packs tab.
- [ ] **Step 6:** Commit `SOURCES.md` and the script (not the zips; add `packs/*.zip` to `.gitignore`).

### Task 7: Module system, config and Visuals tab

**Files:**
- Create: `src/main/java/com/kcvisuals/module/Module.java`, `ModuleManager.java`, `setting/Setting.java`, `BoolSetting.java`, `NumberSetting.java`, `ColorSetting.java`, `ModeSetting.java`, `src/main/java/com/kcvisuals/config/KCConfig.java`, `src/main/java/com/kcvisuals/gui/visuals/VisualsTab.java`
- Test: `src/test/java/com/kcvisuals/config/KCConfigTest.java`

**Interfaces:**
- Produces: `abstract class Module { String id(); String name(); String description(); boolean enabled(); void setEnabled(boolean); List<Setting<?>> settings(); void onEnable(); void onDisable(); }`. `ModuleManager.get(Class<T>)`, `all()`. `KCConfig.save(Path, ModuleManager, HudLayout)` / `load(…)`; unknown keys ignored, missing keys keep defaults. `NumberSetting(min,max,step)` clamps on load.
- [ ] **Step 1: Failing tests:** `roundTripModulesAndSettings`; `unknownModuleIgnored`; `outOfRangeNumberClamped`; `corruptFileFallsBackToDefaults` (file is backed up as `kcvisuals.json.bak`).
- [ ] **Step 2:** FAIL → **Step 3:** implement → **Step 4:** PASS.
- [ ] **Step 5:** Visuals tab: a module list with toggle and description; clicking expands its settings (Toggle, Slider, ColorPicker with alpha, Mode dropdown). Saved on close and on game exit.
- [ ] **Step 6:** Commit `feat: modules, config, visuals tab`.

### Task 8: HUD system and HUD tab/editor

**Files:**
- Create: `src/main/java/com/kcvisuals/hud/HudElement.java`, `HudLayout.java`, `HudRenderer.java` (hooked via Fabric `HudElementRegistry`/`HudRenderCallback`, whichever 0.141.6 provides), elements `Watermark.java`, `PotionHud.java`, `TargetHud.java`, `Keystrokes.java`, `ArmorTotemCounter.java`, `Coordinates.java`, `src/main/java/com/kcvisuals/gui/hud/HudTab.java`, `HudEditorScreen.java`
- Test: `src/test/java/com/kcvisuals/hud/HudLayoutTest.java`

**Interfaces:**
- Produces: `HudLayout` stores per element `(anchorX ∈ {0,0.5,1}, anchorY, offsetX, offsetY)` in scaled pixels; `Rect resolve(String id, int w, int h, int screenW, int screenH)` clamps fully on screen. `TargetHud` reads the last entity the player attacked (via `AttackEntityCallback`) within 8 s.
- [ ] **Step 1: Failing tests:** `anchorRightStaysRight` (element anchored right at 1920 wide is still right-aligned at 854); `clampedWhenTooFar` (offset beyond screen → fully visible); `snapsToNearestAnchorOnDrop`.
- [ ] **Step 2:** FAIL → **Step 3:** implement → **Step 4:** PASS.
- [ ] **Step 5:** Elements: Watermark "KC Visuals | <name> | <fps> FPS | <ping> ms | <server>" with an accent bar; potions with icon and remaining time; Target HUD with head, name, HP bar, armour; Keystrokes WASD+LMB/RMB+CPS; totem/armour counter; XYZ. HUD tab: toggles + "HUD bearbeiten" → `HudEditorScreen` (drag & drop, grid snap, "Zurücksetzen"). Verify in `runClient` at GUI scales 2 and 4.
- [ ] **Step 6:** Commit `feat: HUD and editor`.

### Task 9: World visuals I – Jump Circles, Trajectories, China Hat, Death Marker

**Files:**
- Create: `src/main/java/com/kcvisuals/render/WorldRender.java` (hook: `WorldRenderEvents.AFTER_ENTITIES`/`LAST` if present in 0.141.6, else mixin into `GameRenderer.renderWorld` like coreclient), `render/Shapes.java` (ring, line strip, cone with `RenderLayers` like coreclient's `RenderUtils`), modules `JumpCircles.java`, `Trajectories.java`, `ChinaHat.java`, `DeathMarker.java`
- Test: `src/test/java/com/kcvisuals/render/TrajectorySimTest.java`

**Interfaces:**
- Produces: `TrajectorySim.simulate(Vec start, Vec velocity, double gravity, double drag, int maxSteps)` → `List<Vec>` (own simple `record Vec(double x,double y,double z)` so it is testable without MC); per-projectile constants: pearl/snowball/egg gravity 0.03 drag 0.99, arrow 0.05/0.99 with velocity 3.0×charge, trident 0.05/0.99 velocity 2.5, splash potion 0.05/0.99 velocity 0.5, wind charge no gravity. Collision with blocks is done on the MC side by raycasting each segment.
- [ ] **Step 1: Failing tests:** `pearlFallsAndSlows` (y velocity decreases monotonically; horizontal speed after 10 steps = v·0.99¹⁰ ± 1e-6); `windChargeIsStraight`.
- [ ] **Step 2:** FAIL → **Step 3:** implement → **Step 4:** PASS.
- [ ] **Step 5:** Modules:
  - JumpCircles: ring at the feet on jump/landing; expands 0→radius (default 1.2) over 0.6 s; fades; gradient accent→pink; setting "nur eigene / alle Spieler".
  - Trajectories: arc for the held item only (own hand), colour + line width, end marker at the impact point.
  - ChinaHat: translucent cone above own head, only in third person.
  - DeathMarker: beam + "Tod · 13 m" at the last own death point in the same dimension.
- [ ] **Step 6:** Verify in `runClient` against the reference video (purple arc, pink rings). Commit `feat: jump circles, trajectories, china hat, death marker`.

### Task 10: World visuals II – Hit Color, Damage Numbers, Hit Particles, Hitbox Style

**Files:**
- Create: modules `HitColor.java`, `DamageNumbers.java`, `HitParticles.java`, `HitboxStyle.java`; mixins `OverlayTextureMixin` (hit tint colour), `LivingEntityHealthMixin` or client-side health-delta tracking for damage numbers, `EntityRenderDispatcherMixin` (hitbox line colour/width)
- Create: `src/main/resources/assets/kcvisuals/textures/particle/heart.png`, `star.png`, `spark.png` (own simple 8×8 pixel art)

- [ ] **Step 1:** HitColor: replace the red hurt overlay colour with a `ColorSetting` (default accent at 40% alpha).
- [ ] **Step 2:** DamageNumbers: on a client-observed health decrease of a living entity in range, spawn a floating number "-x.xx" (two decimals, red→accent), which rises and fades over 1 s and is billboarded with a pixel-font look like in the video. Only data the client already gets.
- [ ] **Step 3:** HitParticles: on own attack, spawn N (default 6) client-only particles (heart/star/spark mode, colour) around the target.
- [ ] **Step 4:** HitboxStyle: when F3+B is active, line colour + width settings. Box size unchanged.
- [ ] **Step 5:** Verify in `runClient` on a zombie/armour stand that all four work and are switchable. Commit `feat: hit visuals`.

### Task 11: Camera/screen visuals – Custom Hand, Custom Sky, Fullbright, Crosshair, Aspect Ratio, Render Tweaks

**Files:**
- Create: modules `CustomHand.java`, `CustomSky.java`, `Fullbright.java`, `Crosshair.java`, `AspectRatio.java`, `RenderTweaks.java`; mixins `HeldItemRendererMixin`, `BackgroundRenderer/FogMixin`, `SkyRenderingMixin`, `LightmapMixin` (or gamma option override), `InGameHudCrosshairMixin`, `GameRendererProjectionMixin`, `InGameOverlayRendererMixin` (fire), `TotemAnimationMixin`, `InGameHudPumpkinMixin`, `GameRendererHurtCamMixin`, `ParticleManagerMixin`

- [ ] **Step 1:** CustomHand: main/offhand X/Y/Z offset, scale (0.3–1.5), rotation, swing speed multiplier.
- [ ] **Step 2:** CustomSky: sky top/horizon colour, stars on/off and brightness, fog off, client-only fixed time (visual only).
- [ ] **Step 3:** Fullbright: gamma override while enabled, restore on disable.
- [ ] **Step 4:** Crosshair: modes cross/dot/circle/plus-dot, size, gap, thickness, colour, outline; hides the vanilla crosshair while enabled.
- [ ] **Step 5:** AspectRatio: horizontal projection stretch factor 1.0–1.6.
- [ ] **Step 6:** RenderTweaks: fire overlay height (0–100%) / off, totem pop animation scale (0–100%), pumpkin overlay off, hurt-cam off, explosion/crystal particle multiplier (0–100%).
- [ ] **Step 7:** Verify each in `runClient` with on/off comparison; check that ending the game with modules enabled and restarting restores state without leftover gamma. Commit `feat: camera and screen visuals`.

### Task 12: Final pass

- [ ] **Step 1:** `./gradlew build` → jar in `build/libs/kcvisuals-1.0.0.jar`; all tests pass.
- [ ] **Step 2:** Clean test: fresh 1.21.11 Fabric profile with Fabric API (+ Mod Menu), copy the jar, run `install-packs.ps1`, start the game: `/kcv` works, all packs listed, a 5-category profile applied, all modules toggled once, HUD moved. Also join a multiplayer server once to confirm nothing breaks there.
- [ ] **Step 3:** Write `README.md` (installation, `/kcv`, where packs live, how to add new packs).
- [ ] **Step 4:** Commit `chore: release 1.0.0`.
