# Changelog

Retroactive version report for the full rewrite of Lotus Blight. `mod_version`
was left at `1.0.0` for the entire rewrite instead of being bumped per batch
of work — this file assigns version numbers to the existing commit history
after the fact, without rewriting any past commits. Going forward,
`gradle.properties`' `mod_version` is bumped alongside the commit that
finishes each batch of work below.

Versioning switched from `major.minor.patch` to a flat `1.NNNN` counter at
`1.1010` — every entry below `1.1010` still uses the old three-part numbers
as they were actually committed at the time.

*Note: commit messages between `1.7.0` and `1.67.x` didn't embed a version
number, so that stretch isn't individually reconstructable here — see
`git log` for the raw history of that range. Everything from `1.68.0` onward
is transcribed directly from its own commit message.*

## 1.1050.1
Мелкие правки после релиза — в счёт 50 версий не идут, войдут в полный лог 1.1100.
Small post-release changes — not counted toward the 50 versions, they go into the 1.1100 full log.

### Русский
- Новая лицензия: мод можно включать в бесплатные сборки без изменений и со ссылкой; мы не
  претендуем на персонажей, историю и сюжет DOORS — это AU и хедканоны; проект некоммерческий
  (`LICENSE`).
- README переписан под текущий мод, на русском и английском.
- `ASSETS.md` — список ассетов и их авторов: DOORS (LSPLASH), скин Хончо (Gdux), музыка, текстуры.
- Рекомендуемая сборка с установщиком: `install.bat` ставит моды, шейдеры и настройки в `.minecraft`
  или выбранную папку, а моды, которые нельзя раздавать, скачивает с их официальных страниц.

### English
- A new license: the mod may be included unmodified in free modpacks with a link; we claim no
  authorship of DOORS' characters, story or plot — this is AU and headcanons; the project is
  non-commercial (`LICENSE`).
- The README is rewritten for the current mod, in Russian and English.
- `ASSETS.md` — the assets and their authors: DOORS (LSPLASH), Honcho's skin (Gdux), music, textures.
- A recommended pack with an installer: `install.bat` puts the mods, shader pack and settings into
  `.minecraft` or a chosen folder, and downloads the mods that can't be redistributed from their
  official pages.

## 1.1050 — Release: everything from 1.100 to 1.1050
The first full release of the 1.1000s: 1.100.0 and 1.101.0, the switch to the flat 1.NNNN counter at
1.1010, and every version after it up to this one — grouped by what changed rather than by the order
it landed in. Every 50 versions gets a log like this; the per-version detail for 1.1017–1.1042 stays
in its own section below.

### Русский

**Хончо**
- У Хончо своя модель вместо позаимствованной у зомби, с локтями, коленями и поясницей; стойка,
  поза встречи и молитва на коленях взяты прямо из проекта Blockbench. Позы сперва проигрывались
  зеркально — руки уходили за спину, — теперь стоят ровно так, как задуманы.
- Он больше не зомби даже внутри: отдельное существо, без солнечных ожогов, превращения в
  утопленника, подкреплений, маленьких копий и курочек-наездников. Големы его не трогают.
- 25 сердец, анимация ходьбы, один Хончо на весь мир.
- После третьего флакона держится к игроку ближе, а тому, кто его кормит, иногда говорит, что свет
  больше не ведёт его — теперь его ведёт игрок.
- Встреча на ветке войны стала полноценной катсценой: игрок спотыкается и лежит на земле, камера
  внизу, Хончо протягивает руку. «Да» — он поднимает, радуется, гладит по голове и тут же, в той
  же сцене, рассказывает свою историю и на коленях просит взять его в помощники. «Нет» — молится и
  переспрашивает; второй отказ — уходит из мира навсегда. Во время сцены игрока нельзя ранить, она
  не начинается посреди Побега или боя с боссом и не может «потерять» окно ответа.
- С модом Cinematic встреча получает настоящий кадр с камерой и затемнением.
- Команды `/lotus honcho` для проверки всего перечисленного.

> **От Reranyti:** Окееееей, теперь Хончо… У нас из фиксоооов:
> 25 сердечек — он больше не сдохнет от первого удара. Порадуйтесь, ваш виртуальный муж не сдохнет.
> И у нас появилась поддержка нового мода! Cinematic для катсцен — с ним камера работает намного лучше.
> Теперь исправлен «КАТЯЩИЙСЯ ХОНЧОООО».
> Также добавлены команды для тестов, а я откланиваюсь.

**Заражение**
- Заражение наконец видно: оно растёт по поверхности, а не прячется в камне. Под землю начинает
  уходить с третьей фазы, а на пятой пробивается глубоко — там же появляется лотосовая руда.
- Темп спокойнее — примерно в 3–4 раза медленнее, чем в бете; полмира за пару игровых дней больше
  не заражается.
- Туман в заражённой зоне действительно сгущается, и пятая фаза теперь самая тяжёлая, а не слабее
  второй.
- Созревший очаг (фаза 4) правда становится болотом лотоса — биом меняется на всю высоту, заодно
  догоняют и старые очаги. Для этого пришлось исправить ошибку в самом WorldEdit.
- В родном болоте и на своей территории лотос растёт быстрее — это настоящий бонус, а не
  перекраска.
- Корни больше не превращают воду в твёрдый пол, на суше они редкие; над водой заражается дно.
- Ростки лотоса стоят на воде вместе со своей кувшинкой и не ломаются; очищающий порошок их не
  убирает, но замедляет очаг.
- Лотосовые деревья всегда в цветах лотоса.
- Заражённая вода больше не отдельная жидкость — вода остаётся водой, а заражение живёт в биоме и
  тумане. Вместе с ней ушли устаревшие блоки: лотосовая земля, спутанные корни, сухая древесина
  Блеска.
- У болота лотоса и тундры Блеска появились свои цвета тумана — раньше они брали чужие.
- Мшистые железы больше не пропадают на всю сессию из-за одной неудачной попытки найти им место.
- Корни растут через очередь и не застревают, кувшинки дочерних очагов не ставятся на сушу, а
  Сердце лотоса в мире снова одно.
- Убраны повторяющиеся залпы деревьев, лианы, стиравшие постройки, подвисания из-за загрузки чанков
  и заплатки Блеска прямо во время генерации.

> **От Reranyti:** Ой, бля, моё нелюбимое.
> Ну, теперь короче вся поверхность хорошая! Заражается до… эм… Раньше оно жрало как не в себя, если что, но там был коммит исправлений, и мы понизили его скорость.
> Там наконец-то есть вкусный, сладкий туман — теперь вы будете как ёбек в тумани. Ну и, конечно, логично было исправить то, что лотос такой: «ну билин, это моя территория, а я не расту быстрее» — теперь исправлено.
> Заражённая вода сосёт. Откланиваюсь.

**Сюжет и события**
- Побег и StarFall приходят раньше — либо по заражению мира, либо просто спустя время после выбора
  ветки.
- Страницы дневника Объекта Ноль находятся не только у стражей: и возле созревающих очагов, и за
  пройденный Побег, и без дублей.
- Метеоритный камень опасен Альянсу, а не Войне: ветка войны на стороне Звёздного Света. Добыть его
  теперь можно — раньше не получалось ни у кого, как и нормально копать остальные блоки мода нужными
  инструментами.
- Место падения метеорита стало огромным пятном, сразу считается территорией Блеска (стражи там не
  появляются, лотос растёт медленнее) и отмечается на карте.
- Лаборатория Побега строится без дыр; выйти из игры посреди забега — значит быть пойманным, и
  наказание теперь действительно доходит.
- Чёрные сердца — только на ветке Альянса.
- StarFall больше не обрушивает FPS и не даёт повторять сцену ради выгоды.
- Стражи не превращаются в мирных волков после перезагрузки, арена босса-предателя и ветка его
  достижений работают, споры и холодная порча реально наносят урон.

**Системы**
- Репутация с фракциями — Лотос, Учёные, Звёздный Свет.
- Собственный игровой календарь с виджетом.
- Всё, что игрок прошёл, переживает смерть и возвращение из Края; стартовый набор не выдаётся
  повторно.
- Ответы на сцены проверяются сервером: подделанный клиент больше не выдаст себе концовки и не
  повторит сцены.

**Диалоги**
- С Chat Overhaul реплики Звёздного Света, Мирового Лотоса, Хончо и внутреннего голоса звучат в
  его чате — у каждого свой цвет и своя иконка (раньше Звёздный Свет был с лицом Стива). Наши окна
  диалогов тогда не мешают, а окна выбора остаются нашими.

**Совместимость и производительность**
- Все связи с другими модами собраны в один реестр — `/lotus compat` показывает, что включено.
  Правки чужих модов проверяют, что нужный код есть в установленной версии, и поэтому не ломаются
  ни на старых, ни на новых версиях.
- Исправлены ошибки в зависимостях: WorldEdit (смена биома), Ex Meteor Shower (три его достижения
  не загружались).
- Streams Reflowing: реки вокруг точки появления строятся заранее, при первом открытии мира, и мод
  один раз предлагает ему более лёгкое качество — это главная причина подвисаний на слабых ПК при
  прогрузке новых мест. Своё значение игрока не трогаем, всё выключается в конфиге.
- JourneyMap: метки и зона заражения не пересобираются впустую.
- Мод запускается на выделенном сервере и без TerraBlender; все нужные зависимости объявлены.
- 15 автоматических проверок (GameTest) прогоняют Хончо, распространение, порошок, метеоритный камень,
  лоровые пороги и смену биома; в релизный jar они не входят.

**Мелочи**
- Экранные оверлеи больше не рисуются по двадцать раз за кадр, а таймер Побега идёт по настоящему
  времени.
- Английский перевод (`en_us.json`) — у игроков не на русском больше нет сырых ключей.
- Модель «Витаминки», достижение за очищающий порошок, текстура Хончо в jar, клавиша N при ответе
  Хончо, сброс клиентских состояний при выходе из мира, страница дневника засчитывается при подборе,
  а не при выпадении, пасхалка мира с одним биомом, рассинхрон ягод и жезла, частицы, которые не
  появлялись.

### English

**Honcho**
- Honcho has a model of his own instead of a borrowed zombie body, with elbows, knees and a waist;
  his idle, meeting and kneeling prayer poses come straight from the Blockbench project. They first
  played mirrored — arms swinging toward his back — and now sit exactly as designed.
- He's no longer a zombie underneath either: a creature of his own with no sunburn, no Drowned
  conversion, no reinforcements, no baby copies or chicken jockeys. Golems leave him alone.
- 25 hearts, a walking animation, one Honcho per world.
- After the third vial he stays closer to the player, and now and then tells whoever feeds him that
  the light doesn't guide him anymore — the player does.
- The war-branch meeting is a real cutscene now: the player trips and lies on the ground, the camera
  low, and Honcho offers a hand. Yes — he pulls them up, is glad, pats their head and, still in the
  same scene, tells his story and kneels to ask to be their assistant. No — he prays and asks again;
  a second no and he leaves the world for good. The player can't be hurt during it, it never starts
  mid-Chase or mid-boss-fight, and it can't lose its answer window.
- With the Cinematic mod the meeting gets a proper camera shot and fade.
- `/lotus honcho` commands to test all of the above.

> **From Reranyti:** Okaaaay, now Honcho… Our fixeeees:
> 25 hearts — he won't drop dead from the first hit anymore. Rejoice, your virtual husband won't die.
> And we now support a new mod! Cinematic for cutscenes — the camera works much better with it.
> The "ROLLING HONCHOOOO" is fixed now.
> Test commands were added too, and I'm taking my leave.

**Infection**
- The infection is finally visible: it grows across the surface instead of hiding in the rock. It
  starts reaching underground from phase 3 and at phase 5 pushes deep — that's where lotus ore forms.
- A calmer pace — roughly 3-4 times slower than in the beta; half the world no longer falls in a
  couple of in-game days.
- The fog in infected land really thickens, and phase 5 is now the heaviest instead of weaker than
  phase 2.
- A mature outbreak (phase 4) truly becomes lotus marsh — the biome changes over the full height, and
  older outbreaks catch up. That took fixing a bug in WorldEdit itself.
- In its home marsh and its own territory the lotus grows faster — a real bonus, not a recolor.
- Roots no longer turn water into a solid floor and are rare on land; over water the bottom gets
  infected.
- Lotus shoots sit on the water with their own lily pad and can't be broken; cleansing powder leaves
  them but slows their outbreak.
- Lotus trees always wear the lotus palette.
- Infected water is no longer a separate fluid — water stays water, the infection lives in the biome
  and the fog. Outdated blocks went with it: lotus soil, tangled roots, dry Blessing wood.
- Lotus marsh and Blessing tundra have fog colors of their own — they used to borrow others'.
- Mossy glands no longer vanish for the whole session after one failed attempt to place them.
- Roots grow through a queue and don't get stuck, child outbreaks don't put lily pads on dry land,
  and there's only ever one Lotus Heart in the world again.
- Gone: repeating tree bursts, vines wiping out builds, hitches from chunk loading, and Blessing
  patches placed mid-generation.

> **From Reranyti:** Oh damn, my least favorite.
> So now, basically, the whole surface is good! It infects up to… uh… It used to eat everything like crazy, just so you know, but there was a fix commit and we slowed it down.
> There's finally a tasty, sweet fog — now you'll be like a hedgehog in the fog. And of course it made sense to fix the lotus going "ugh, this is my territory and I don't grow any faster" — fixed now.
> Infected water sucks. I'm taking my leave.

**Story and events**
- The Chase and StarFall arrive sooner — by world infection, or simply some time after choosing a
  branch.
- Object Zero diary pages don't only come from guardians anymore: also near maturing outbreaks and
  for surviving the Chase, never as duplicates.
- Meteorite stone is deadly to the Alliance, not the war branch: the war branch is on Star Light's
  side. It can actually be mined now — nobody could before, and the mod's other blocks didn't take
  the right tools either.
- A meteor impact leaves a huge patch, counts as Blessing territory right away (no guardians, slower
  lotus) and shows on the map.
- The Chase lab is built without holes; logging out mid-run means being caught, and the penalty
  actually lands now.
- Black hearts belong to the Alliance branch only.
- StarFall no longer tanks the frame rate or lets its scene be replayed for gain.
- Guardians don't turn into passive wolves after a restart, the traitor boss arena and its
  advancement branch work, spores and the cold blight really deal damage.

**Systems**
- Reputation with factions — the Lotus, the Scientists, Star Light.
- A game calendar of its own with a HUD widget.
- Everything a player has been through survives death and leaving the End; the starter kit isn't
  handed out twice.
- Scene answers are checked by the server: a modified client can't hand itself endings or replay
  scenes anymore.

**Dialogue**
- With Chat Overhaul the lines of Star Light, the World Lotus, Honcho and the inner voice are spoken
  in its chat — each with a name color and an icon of their own (Star Light used to wear Steve's
  face). Our dialogue boxes step aside then; the choice windows stay ours.

**Compatibility and performance**
- Every link to another mod is gathered in one registry — `/lotus compat` shows what's active.
  Patches to other mods check that the code they need exists in the installed version, so they hold
  up on older and newer versions alike.
- Bugs fixed inside dependencies: WorldEdit (biome changes), Ex Meteor Shower (three of its
  advancements never loaded).
- Streams Reflowing: the rivers around spawn are built up front the first time a world opens, and the
  mod suggests a lighter river quality once — the main cause of hitches on weaker PCs while new land
  loaded. A value the player chose is left alone, and it can all be turned off in the config.
- JourneyMap: markers and the infection area aren't rebuilt for nothing.
- The mod runs on dedicated servers and without TerraBlender; every required dependency is declared.
- 15 automated checks (GameTest) run through Honcho, spread, the powder, meteorite stone, the lore
  thresholds and the biome change; they're left out of the release jar.

**Small things**
- HUD overlays are no longer drawn twenty times a frame, and the Chase timer runs on real time.
- An English translation (`en_us.json`) — players not on Russian no longer see raw keys.
- A model for the vitamin item, the cleansing powder advancement, Honcho's texture in the jar, the N
  key while answering Honcho, client state reset when leaving a world, diary pages counted on pickup
  rather than on drop, the single-biome world easter egg, berry and rod desync, particles that never
  showed.

## 1.1042 — Beta: full audit release (1.1017 – 1.1042)
A full read-through of the whole mod (175 classes, resources, design docs) with every confirmed
bug fixed, then checked by building the jar and starting a dedicated server until `Done`. One
commit per fix, kept on the `fix/audit-2026-09` branch for reference.

Critical:
- Player state (branch, traitor flag, Honcho progress, StarFall, reputation, one-time gifts) was
  wiped on every death and End exit - Forge only carries over `PlayerPersisted`. Now copied on clone.
- Meteorite stone could never be obtained: no `mineable` tags existed for any block of the mod,
  and mining progress read an always-empty client state. The contact kill for the war branch never
  fired either (`entityInside` on a full cube) - now `stepOn` and punching it.
- Honcho, a Zombie underneath, burned at dawn, turned into a Drowned underwater, was attacked by
  golems and could call reinforcements. All disabled; he now spawns once per world.
- StarFall: the grafting rod was never removed (`removeItem` compares by reference), and the
  client-sent line packet could be replayed to drop the radius-24 meteor patch at will.
- Black hearts for golden items now apply to the Alliance branch (the one Star Light warns).

Major:
- Phase 4 biome rewrite only changed the Y=0 biome cell (WorldEdit's 2D `setBiome`); now every
  4x4x4 cell over the full height.
- Guardians turned into passive wolves after a restart or chunk reload (goals aren't saved).
- All six HUD overlays were drawn once per HUD layer (20+ times a frame); the chase clock counted
  frames instead of ticks and ran out in seconds.
- The traitor advancement branch never loaded (missing parent `follow_the_current`).
- Lotus spores and cold blight damage almost never landed (two unrelated counters).
- Starter kit handed out again when logging in from another dimension.
- The phase 4 tree burst, biome rewrite and announcement replayed whenever the block count dipped
  under a threshold and climbed back - outbreaks now remember their peak phase.
- Synchronous chunk loads in the spread engine; vine barriers and the boss arena overwrote chests.
- Traitor boss arena was built inside the player; leftover wave mobs stayed in the world forever.
- Roots and leaves never applied spores on contact.

Found by starting a dedicated server:
- The mod failed to construct without TerraBlender (class verification needed `terrablender.api.Region`
  before the `isLoaded` check). TerraBlender is now a required dependency.
- Client-only rendering, color handlers and the wiki/journal screens were referenced from common
  classes, so the mod never loaded on a dedicated server.

Dependencies:
- Ex Meteor Shower needs OctoLib (and OctoLib needs Architectury) without declaring it - both are
  now listed as required, so a missing one is reported by the loader instead of crashing.
- Chat Overhaul declared as an optional dependency.

Smaller fixes: packet directions and server-side validation, Honcho scenes re-shown if left
unanswered, client state and music reset when leaving a world, the Honcho "N" answer no longer
opens the atlas, the chase lab waits for its whole footprint to load and leaving mid-run counts as
being caught, roots queue, lily pads on dry land, a second heart from the epicenter, scientist
pages counted on pickup, `en_us.json` fallback, Honcho idle pose (the animation never played
because of a name mismatch), boss wave count corrected to 5 in the docs.

## 1.1011 – 1.1016
- Faction reputation; Honcho moves closer after the 3rd vial (1.1011).
- Honcho gets his own GeckoLib model instead of the zombie one, plus the trip-meeting scene (1.1012).
- Infected water tint per biome fixed; Mossy Glands could fail to appear at all (1.1013).
- Lotus territory as a real spread bonus, `honcho.geo.json` fix, own calendar (1.1014).
- Real phase 4 biome transition through WorldEdit; infected water removed as a separate fluid (1.1016).

## 1.1010 — Versioning switch
- Switched `mod_version` from `major.minor.patch` to a flat `1.NNNN` counter.

## 1.101.0 — Honcho ambient line
- Rare ambient line for whoever is currently feeding Honcho, echoing "the
  light used to guide him, now the player does" without quoting anything.

## 1.100.0 — Meteorite patches, Blessing territory, map waypoints
- StarFall's meteorite impact patch radius widened substantially (9 → 24).
- Impact sites now carry their own "territory" flag (not a real Minecraft
  biome) recognized by the same checks that already treat the real Blessing
  biome specially (guardian spawn skip, infection spread penalty) — set the
  instant the site is seeded, ahead of the slower block-by-block spread.
- Meteorite impact positions now show as waypoints on the minimap.

## 1.96.0 – 1.99.0 — Honcho
- New original quest NPC appearing once after a player survives StarFall:
  brings him a Star Light Vial, gets a permanent health blessing and lore in
  return. A short-lived duplicate NPC ("Horichoniy", a WanderingTrader-based
  vendor) was added by mistake and removed again the same day.
- War-branch-only "let me be your assistant" scene on first meeting, with a
  Yes/No choice each granting its own advancement.
- The vial quest became repeatable instead of one-time: every vial deepens a
  real dependency — Honcho follows whoever fed him and visibly weakens
  (Weakness + Slowness) if 20 minutes pass without another one.

## 1.93.0 – 1.94.1 — Quarantine world border
- World border capped at 1,000,000 blocks, centered on spawn, for story
  reasons ("the site was sealed under quarantine").
- Fully custom (not vanilla) purple force-field wall rendered at the nearest
  edge, plus a bounce-back-and-can't-die-from-it mechanic replacing vanilla's
  own border damage, plus a blood-red sky tint on approach.

## 1.91.0 – 1.92.0 — Per-biome infection color overhaul
- Every biome with a dictated fog gradient now derives its own ground/wood
  tint from that gradient instead of one of 8 shared group colors.
- Stone/sand/gravel intentionally kept one shared tone across every biome
  (they're minerals, not biome-tied organics) — fixed from an accidental
  identical color bug, not turned into 50 more variants.
- Infected water's underwater fog now follows the same per-biome gradient
  instead of one fixed dark green everywhere.
- Infection spread speed now fades smoothly with distance from the nearest
  player instead of freezing outbreaks outside a fixed radius entirely.

## 1.87.0 – 1.90.0 — Post-audit bug-fix wave
- A second full-mod workflow audit found and fixed: an unguarded
  synchronous-chunk-load deadlock (also present in three other spots besides
  the one already fixed in the Escape lab), a starter-water-search fairness
  bug starving later-joining players, grass blocks almost never actually
  converting during infection spread, and tree canopies starving ground
  conversion of frontier attempts.
- Several one-off crashes/bugs on specific Forge builds and StarFall's own
  branch mix-up were fixed the same week.

## 1.85.0 – 1.86.0 — Meteorite gear, real StarFall trigger
- Crafting recipes for the whole `lotus_alloy` tool/armor set, the grafting
  rod, seer lens and vitamin — previously creative-only.
- StarFall now fires on its own once world infection crosses 30%, instead of
  only via the admin test command.
- Full meteorite tool/armor tier (between diamond and netherite).

## 1.68.0 – 1.84.0 — Traitor branch, Escape, StarFall
- Full traitor storyline: guardian-kill/cleanse-powder betrayal triggers, a
  5-wave boss fight, and its own incineration cutscene.
- "Побег от лотоса" — a persistent, sealed escape-the-lab event with a
  real/decoy junction, unlocked only once world infection crosses 15%.
- StarFall — a one-time Star Light encounter with a full alliance/war branch
  script, ending in either a permanent resource or a real consequence
  depending on the player's choices, backed by the Ex Meteor Shower mod for
  its comet visuals.

## 1.6.0 — Inner voice system, glowing berries fixed
- `glowing_berry` was registered but unreachable (bush dropped itself, not the
  food item) — right-click the bush to harvest instead of breaking it.
- New True Light effect (opposite of Lotoniriya) and a second, small
  "dialogue system": eating a glowing_berry shows a short subtitle-style
  scene (player's own skin/name as the portrait, not the Lotus), advanced
  with Enter, with a gold vignette and its own music cue.

## 1.5.0 — Full art pass (sprite sheet)
- Replaced 12 item textures and ~19 block textures with hand-drawn sprite
  sheet art, replacing the earlier procedural Pillow textures.
- Split `infected_lotus.png` from the GeckoLib crown's box-UV atlas (they
  used to share one file, which would have scrambled the crown's geometry).
- `blessing_nodule` intentionally left on the old texture (new art has
  transparent gaps unsuitable for a cube_all block).

## 1.4.0 — Live bug-fix pass (config, cleansing, armor, ore, biome, clustering)
- `ACTIVE_CHUNK_RADIUS`, `SPREAD_RADIUS`, `MAX_PENDING_WORLDGEN_TASKS` config
  values wired up (previously read nowhere).
- Blessing biome's wiki-claimed spread slowdown actually implemented.
- Fixed JourneyMap version-range crash (`1.20.1-6.0.5` compared as < `6.0`).
- Cleansing powder now actually decreases outbreak progress.
- Armor texture lookup fixed (missing namespace) and a real texture added.
- `lotus_ore` given worldgen placement (previously could never spawn).
- Blessing biome moved out of TerraBlender's snow-generating climate zone.
- Mini-lotus shoot spam clustering fixed with a spacing check.

## 1.3.0 — Spread engine deep fixes
- Fixed the "pillars instead of lotuses" bug (ordinary spread was placing a
  full 4-tall anchor instead of a small shoot).
- Root-grown child mini-outbreaks now place a visible flower.
- Open-ocean spread sped up but capped below the mini-biome phase.
- Lotus Heart now only appears at real phase-4 maturity, not instantly from
  a planted seed.
- Guaranteed distant mini-biome epicenter (5000-9000 blocks from spawn).
- Admin `/lotus` command tree for testing outbreaks/branches/map state.

## 1.2.0 — Dialogue system rewrite
- Branch choice (Alliance/Resistance) now actually persists, locks
  permanently server-side, and influences real spread speed near the player
  — previously it only gated one tool and reset every time the dialogue
  screen reopened.
- Rewrote all dialogue lines for a more natural tone; wired previously dead
  lore content (scientist's journal, village water crisis) into the flow.

## 1.1.0 — Integrations and item audit
- GeckoLib bloom animation for the infected lotus crown.
- Soft TerraBlender integration for biome placement.
- JourneyMap waypoint integration (replacing the mod's own HUD map when
  JourneyMap is installed).
- YACL-based in-game config screen.
- Curios API investigated for the seer lens, reverted before shipping —
  would have hard-crashed the mod for anyone without Curios installed.
- Fixed a held-item render bug and an Embeddium chunk-mesh crash on
  infected water.
- Item/lang audit: missing translation keys, dead duplicate advancement key.

## 1.0.0 — Full rewrite baseline
- Complete rewrite from the broken `story-v11` prototype: persisted
  SavedData-backed outbreak/spread engine (replacing a non-persistent static
  map), full item/armor/tool set, worldgen (biomes, mini-biome, guaranteed
  spawn), lore/dialogue foundation, procedural art pass.
