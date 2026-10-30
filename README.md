# Testohd Styles – Fabric-Mod für Minecraft 1.21.11
**Made by Testohd23**

## Mod bauen (Windows, ein Doppelklick)
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
Beim ersten Start erzeugt die Mod 49 cleane eigene Packs (Farben, Duo-Packs mit passendem Farbwechsel, Farbverläufe, Regenbogen, Galaxie, Weihnachten) in `.minecraft/testohd-styles/packs/`
(aus deinen eigenen Minecraft-Texturen umgefärbt + selbst gezeichnete Pixel-Art):
- **Testohd Neon Lila**, **Testohd Blutrot**, **Testohd Ozean** – PvP-Farbpacks
- **Testohd Weihnachten Zuckerstange** – Zuckerstangen-Schwerter, Christbaumkugeln statt Enderperlen, Totem mit Weihnachtsmütze, Stern-Fadenkreuz, Schnee auf Gras
- **Testohd Weihnachten Frost** – Eis-Look, Schneeflocken-Fadenkreuz und -Spinnweben, verschneite Blöcke
- **Testohd Weihnachten Tannenbaum** – Tannengrün mit roten Griffen, Lichterketten-Obsidian, Goldkugeln, Tannenbaum-Fadenkreuz
Deine Auswahl, Einstellungen, Favoriten, Profile und eigenen Packs bleiben bei Updates erhalten
(Sicherungskopie in `.minecraft/testohd-styles/backup/`).
Neu erzeugen: Einstellungen → Testohd Styles → „Eingebaute Packs neu erzeugen“.

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
- **Kopf:** Krone, Heiligenschein, Zylinder, Weihnachtsmütze, Hörner, Strohhut, Katzenohren
- **Gesicht:** Sonnenbrille, Monokel, Maske · **Rücken:** Engelsflügel, Teufelsflügel, Rucksack
- **Effekte:** Partikel-Auren, die um dich kreisen – Sternenstaub, Seelenfeuer, Flammen, Schneesturm,
  Kirschblüten, Magie, Herzen, Musik
- Seltenheiten (Gewöhnlich/Selten/Episch/Legendär); legendäre Teile glänzen (Verzauberungs-Schimmer)
- Animationen: Heiligenschein schwebt und dreht sich, Flügel schlagen, Partikel-Akzente
  (Heiligenschein funkelt, Krone glitzert, Weihnachtsmütze schneit, Teufelsflügel rauchen ...)
- **Nur du siehst sie** (clientseitig). Effekte in der Ego-Sicht sind abschaltbar.

## Eigene Kategorien
`.minecraft/testohd-styles/categories.json` – Pfade relativ zu `assets/minecraft/`, `*` = Dateiname,
`**` = auch Unterordner. Optional `"cube": [oben, seite]` für 3D-Vorschau.

## Technik
Mojang-Mappings, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.19.
Mixins (Zoom, Fullbright, Low Fire, Item-Position, Wetter) sind optional: falls eine nicht greift,
fehlt nur diese Funktion, das Spiel stürzt nicht ab.
