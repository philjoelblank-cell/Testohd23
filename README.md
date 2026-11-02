# Testohd Styles – Fabric-Mod für Minecraft 1.21.11
**Made by Testohd23**

## Fertige Mod herunterladen (auch für Freunde)
Die fertige `.jar` gibt es unter **Releases** auf https://github.com/philjoelblank-cell/Testohd23 .
In den mods-Ordner legen (Minecraft 1.21.11 + Fabric Loader + Fabric API) – danach **updatet sich die Mod von selbst**.

## Mod selbst bauen (Windows, ein Doppelklick)
1. Zip entpacken (Rechtsklick → „Alle extrahieren“).
2. **`MOD_BAUEN.bat` doppelklicken.**
   - Fehlt Java 25 (wird zum Bauen gebraucht), installiert das Skript es automatisch.
   - Beim ersten Mal lädt Gradle Minecraft + Fabric herunter (ein paar Minuten).
   - Die fertige `testohd-styles-1.0.0.jar` wird automatisch in `%APPDATA%\.minecraft\mods` kopiert.
3. **Fabric API** (für 1.21.11) muss auch im mods-Ordner liegen.
4. Minecraft 1.21.11 mit Fabric Loader starten → im Spiel **K** drücken.

Wenn der Build fehlschlägt, öffnet sich `build-log.txt` – die Datei an Claude schicken.

## Was die Mod kann

### Texturen mischen (Taste K)
- Oben eine Leiste mit allen Kategorien (wie im Video): Obsidian, Endkristalle, Seelenanker, Glowstone,
  Grasblöcke, Totems, Enderperlen, Tränke, Goldäpfel, Schwerter, Äxte, Spitzhacken, Schaufeln, Streitkolben,
  Bögen, Windkugeln, Spinnweben, Rüstung, Elytren, Verzauberungsglanz, Truhen, Inventar, Himmel, Fadenkreuz
  + extra: Schilde, Armbrüste, Dreizacke, Schneebälle, XP-Flaschen, Feuer, Hotbar, Herzen.
- Darunter Karten mit Vorschau (Blöcke in 3D) – Klick = auswählen. Mausrad scrollt.
- Packs hinzufügen: **„+ Packs“**, Packs **ins Fenster ziehen**, oder in `.minecraft/testohd-styles/packs/` legen.
- **Fertig** → Texturen werden angewendet. Alte 1.8-Packs werden automatisch umgerechnet.

### Eingebaute Packs (Made by Testohd23)
Beim ersten Start erzeugt die Mod 49 cleane Packs in `.minecraft/testohd-styles/packs/` (aus deinen eigenen
Minecraft-Texturen umgefärbt): einfarbige Packs, Duo-Packs mit passendem Farbwechsel, Farbverläufe, Regenbogen,
Galaxie und Weihnachten. Jedes Pack enthält:
- Items, Blöcke, Herzen, Hungerleiste, Hotbar, Fadenkreuz, Verzauberungsglanz, Feuer
- was man platziert/trägt/wirft: Endkristall, Schild (mit Emblem), Rüstung am Körper, Elytra-Flügel, Dreizack, Windkugel, Truhen
- einen eigenen Himmel im Stil der großen PvP-Packs: Tag-Himmel mit großen Wolken, Nacht-Himmel mit leuchtenden
  Wolkenrändern, Sternen und Bergen am Horizont, Sonne als leuchtender Stern (Galaxie: Weltraum-Himmel)
- Low Fire (Feuer ist niedriger und verdeckt die Sicht nicht)
Deine Auswahl, Einstellungen, Favoriten, Profile und eigenen Packs bleiben bei Updates erhalten
(Sicherungskopie in `.minecraft/testohd-styles/backup/`).
Neu erzeugen: Einstellungen → Testohd Styles → „Eingebaute Packs neu erzeugen“.

### Eigene Himmel (Skyboxen)
Sky-Packs im OptiFine/MCPatcher-Format (`assets/minecraft/optifine/sky/world0/sky*.properties`) werden von der Mod
selbst gezeichnet – Kategorie „Himmel“ auswählen, fertig (Tag/Nacht-Überblendung, Drehung, Blend add/alpha).

### Online-Packs (⬇ Online)
Suche echte Texture Packs auf **modrinth.com** direkt im Spiel (Beliebt, PvP, Weihnachten, Winter, Dunkel, Neu
oder eigene Suche) und lade sie mit einem Klick in deinen Packs-Ordner. Die Rechte an diesen Packs liegen bei
ihren Autoren – sie werden nicht mit der Mod verteilt, sondern direkt von Modrinth geladen.

### Einstellungen
Tabs „Einstellungen“ und „Sounds“ (Lautstärke aller Sound-Kategorien).
Item-Position, Verzauberungsglanz (Stärke/Tempo), Zoom (Taste C halten), Fullbright, Klares Wetter,
Partikel, Low Fire, **Ancient Debris** (leuchtender Rahmen, nur Textur – kein X-Ray),
**TT – Totem-Tracker** (Chat-Meldung + Zähler, wenn Spieler in deiner Nähe ein Totem verbrauchen),
**Sprache**, Steuerung (Tasten ändern), Testohd-Styles-Optionen, 3 Profile.

### Cosmetics
- **Kopf:** Krone, Heiligenschein, Zaubererhut, Wikingerhelm, Zylinder, Weihnachtsmütze, Hörner, Kopfhörer, Strohhut, Katzenohren
- **Gesicht:** Sonnenbrille, Monokel, Maske · **Rücken:** Phönixflügel, Engelsflügel, Teufelsflügel, Rucksack
- **Effekte:** Lichtflügel, Regenbogen-Ring, Galaxie-Orbit, Totem-Wirbel, Sternenstaub, Seelenfeuer, Blitz,
  Void-Strudel, Flammenring, Schneewolke, Kirschblüten, Magie, Herzen, Musik
- Seltenheiten (Gewöhnlich/Selten/Episch/Legendär); legendäre Teile glänzen (Verzauberungs-Schimmer)
- Animationen: Heiligenschein schwebt und dreht sich, Flügel schlagen, Partikel-Akzente
  (Heiligenschein funkelt, Krone glitzert, Weihnachtsmütze schneit, Teufelsflügel rauchen ...)
- **Andere mit der Mod sehen deine Cosmetics** (und du ihre). Dafür schickt die Mod nur deine Spieler-UUID und die
  Namen deiner Cosmetics an einen öffentlichen ntfy.sh-Kanal. Beides kann in den Einstellungen ausgeschaltet werden.
- Effekte in der Ego-Sicht sind abschaltbar.

## Eigene Kategorien
`.minecraft/testohd-styles/categories.json` – Pfade relativ zu `assets/minecraft/`, `*` = Dateiname,
`**` = auch Unterordner. Optional `"cube": [oben, seite]` für 3D-Vorschau.

## Technik
Mojang-Mappings, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.19.
Mixins (Zoom, Fullbright, Low Fire, Item-Position, Wetter) sind optional: falls eine nicht greift,
fehlt nur diese Funktion, das Spiel stürzt nicht ab.
