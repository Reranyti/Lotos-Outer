"""Builds the recommended-pack archive (LotusBlight-Pack-<version>.zip) for players.

Takes the mods, shader packs and mod configs from a working Minecraft folder (the one the pack is
tested in), our own jars from build/libs, and packs them with the installer (install.bat /
install.ps1). Mods whose licenses don't allow redistribution are not packed - they're listed in
DOWNLOADS and the installer fetches them from their official Modrinth pages, checking the sha1.
Credits (author, license, links) are read from each mod jar's own metadata.

Usage:  python tools/pack/build_pack.py [path to .minecraft]
Output: build/pack/LotusBlight-Pack-<version>.zip
"""
import hashlib
import io
import json
import os
import re
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HERE = os.path.dirname(os.path.abspath(__file__))
MC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ["APPDATA"], ".minecraft")

FORGE_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html"

# Downloaded by the installer from the official pages, never packed: All Rights Reserved mods, and
# Ex Meteor Shower - GPL, but its authors don't publish the source, so we can't meet GPL's source
# requirement for passing the binary on ourselves.
DOWNLOADS = [
    {"name": "Ex Meteor Shower", "prefix": "meteor_shower-",
     "url": "https://mediafilez.forgecdn.net/files/7756/940/meteor_shower-0.0.4.2.jar",
     "page": "https://www.curseforge.com/minecraft/mc-mods/ex-meteor-shower"},
    {"name": "OctoLib", "prefix": "OctoLib-FORGE-",
     "url": "https://cdn.modrinth.com/data/RH2KUdKJ/versions/HZ7KmyXp/OctoLib-FORGE-0.5.0.1%2B1.20.1.jar",
     "page": "https://modrinth.com/mod/shatterbyte-lib"},
    {"name": "Streams Reflowing", "prefix": "StreamsReflowing-",
     "url": "https://cdn.modrinth.com/data/oLS8HdJ1/versions/fbp6uedq/StreamsReflowing-1.20.1-forge-2.13.1.jar",
     "page": "https://modrinth.com/mod/streams-reflowing"},
    {"name": "JourneyMap", "prefix": "journeymap-",
     "url": "https://cdn.modrinth.com/data/lfHFW1mp/versions/3pOseiLA/journeymap-forge-1.20.1-6.0.5.jar",
     "page": "https://modrinth.com/mod/journeymap"},
]

# Mod configs that make up the pack's tuned setup (paths relative to the Minecraft folder).
CONFIGS = [
    "config/bathymetry.json5", "config/cubes_without_borders.json", "config/curios-client.toml",
    "config/curios-common.toml", "config/embeddium-options.json", "config/ferritecore-mixin.toml",
    "config/immediatelyfast.json", "config/jade", "config/jei", "config/meteor_shower",
    "config/meteor_shower_natural.toml", "config/modernfix-common.toml", "config/naturescompass-client.toml",
    "config/naturescompass-common.toml", "config/oculus.properties", "config/streamsreflowing",
    "config/streamsreflowing-client.toml", "config/streamsreflowing-common.toml", "config/terrablender.toml",
    "config/worldedit", "config/yacl.json5", "journeymap/config/6.0",
]

OWN_PREFIXES = ("lotusblight-", "lotuslib-")

# Source repositories for GPL/LGPL mods whose jars don't link one - their licenses ask for the source
# to be pointed at when the binary is passed on. Keyed by jar file name prefix.
SOURCES = {
    "embeddium-": "https://github.com/embeddedt/embeddium",
    "spark-": "https://github.com/lucko/spark",
    "yet_another_config_lib": "https://github.com/isXander/YetAnotherConfigLib",
    "worldedit-": "https://github.com/EngineHub/WorldEdit",
    "architectury-": "https://github.com/architectury/architectury-api",
    "ferritecore-": "https://github.com/malte0811/FerriteCore",
    "Jade-": "https://github.com/Snownee/Jade",
    "TerraBlender-": "https://github.com/Glitchfiend/TerraBlender",
    "curios-": "https://github.com/TheIllusiveC4/Curios",
    "oculus-": "https://github.com/Asek3/Oculus",
    "modernfix-": "https://github.com/embeddedt/ModernFix",
    "jei-": "https://github.com/mezz/JustEnoughItems",
}
# GPL/LGPL mods whose source repository we haven't found yet (none bundled right now).
SOURCE_UNKNOWN = ()


def mod_version():
    props = open(os.path.join(REPO, "gradle.properties"), encoding="utf-8").read()
    return re.search(r"^mod_version=(\S+)", props, re.M).group(1)


def sha1(path):
    return hashlib.sha1(open(path, "rb").read()).hexdigest()


def meta(path):
    """displayName, version, authors, license and links from a jar's META-INF/mods.toml."""
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        toml = z.read("META-INF/mods.toml").decode("utf-8", "replace") if "META-INF/mods.toml" in names else ""
        manifest = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in names else ""

    def field(name):
        # Triple-quoted, double-quoted or single-quoted - each only ends at its own quote, so an
        # apostrophe inside a double-quoted value ("Nature's Compass") stays part of it.
        for pattern in (r'"""(.*?)"""', r'"([^"\n]*)"', r"'([^'\n]*)'"):
            m = re.search(r'^\s*' + name + r'\s*=\s*' + pattern, toml, re.M | re.S)
            if m:
                return m.group(1).strip()
        return ""

    info = {k: field(k) for k in ("displayName", "version", "authors", "license", "displayURL", "issueTrackerURL")}
    if not info["version"] or "${" in info["version"]:
        # Filled in by the mod's own build - the real value is in its manifest.
        m = re.search(r"^Implementation-Version:\s*(\S+)", manifest, re.M)
        info["version"] = m.group(1) if m else ""
    return info


def main():
    version = mod_version()
    libs = os.path.join(REPO, "build", "libs")
    own = [os.path.join(libs, f"{p}{version}.jar") for p in OWN_PREFIXES]
    for jar in own:
        if not os.path.exists(jar):
            sys.exit(f"missing {jar} - run ./gradlew build first")

    entries = {}  # path inside files/ -> source path
    credits = []
    downloads = []
    mods_dir = os.path.join(MC, "mods")
    for name in sorted(os.listdir(mods_dir)):
        src = os.path.join(mods_dir, name)
        if not name.endswith(".jar") or name.startswith(OWN_PREFIXES):
            continue
        info = meta(src)
        dl = next((d for d in DOWNLOADS if name.startswith(d["prefix"])), None)
        if dl:
            downloads.append({"name": dl["name"], "folder": "mods", "file": name, "url": dl["url"],
                              "sha1": sha1(src), "page": dl["page"]})
            credits.append((info, name, "скачивается установщиком / downloaded by the installer: " + dl["page"]))
        else:
            entries[f"mods/{name}"] = src
            credits.append((info, name, "в архиве / bundled"))
    for jar in own:
        entries[f"mods/{os.path.basename(jar)}"] = jar

    shaders = os.path.join(MC, "shaderpacks")
    for name in sorted(os.listdir(shaders)):
        entries[f"shaderpacks/{name}"] = os.path.join(shaders, name)

    for rel in CONFIGS:
        src = os.path.join(MC, rel)
        if os.path.isdir(src):
            for root, _, files in os.walk(src):
                for f in files:
                    full = os.path.join(root, f)
                    entries[os.path.relpath(full, MC).replace("\\", "/")] = full
        elif os.path.isfile(src):
            entries[rel] = src

    lines = [f"Lotus Blight {version} — рекомендуемая сборка / recommended pack", "",
             "Сборка раздаёт моды их авторов для удобства установки; мы не владеем этими проектами, все права",
             "принадлежат их авторам. Моды, чьи лицензии не разрешают раздачу, установщик скачивает с их",
             "официальных страниц.",
             "The pack distributes other authors' mods for convenience; we don't own those projects, all rights",
             "belong to their authors. Mods whose licenses don't allow redistribution are downloaded by the",
             "installer from their official pages.", "",
             "Шейдеры / Shaders: Complementary Reimagined by Complementary Development (EminGT) —",
             "https://modrinth.com/shader/complementary-reimagined — Complementary License Agreement 1.7;",
             "в сборку включены без изменений / included unmodified.", "",
             "Моды / Mods (исходный код GPL/LGPL-модов — по ссылкам ниже / source of the GPL/LGPL mods at the links below):", ""]
    for info, file, how in credits:
        source = next((url for prefix, url in SOURCES.items() if file.startswith(prefix)), "")
        if not source and file.startswith(SOURCE_UNKNOWN):
            source = "исходники: уточнить / source: to confirm"
        links = " | ".join(x for x in (info["displayURL"], info["issueTrackerURL"], source) if x)
        lines.append(f"- {info['displayName'] or file} {info['version']} — {info['authors'] or '?'} — {info['license'] or '?'}")
        lines.append(f"  {file}; {how}" + (f"; {links}" if links else ""))
    credits_text = "\n".join(lines) + "\n"

    manifest = {"version": version, "forgeUrl": FORGE_URL, "downloads": downloads}

    out_dir = os.path.join(REPO, "build", "pack")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"LotusBlight-Pack-{version}.zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for inside, src in sorted(entries.items()):
            z.write(src, "files/" + inside)
        # cmd.exe wants CRLF line endings in a .bat, whatever the checkout used.
        bat = open(os.path.join(HERE, "install.bat"), encoding="utf-8").read().replace("\r\n", "\n")
        z.writestr("install.bat", bat.replace("\n", "\r\n"))
        # Windows PowerShell 5.1 reads scripts without a BOM as ANSI - the Cyrillic messages need one.
        script = open(os.path.join(HERE, "install.ps1"), encoding="utf-8-sig").read()
        z.writestr("install.ps1", "﻿".encode("utf-8") + script.encode("utf-8"))
        z.writestr("pack.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        z.writestr("CREDITS.txt", credits_text)
        z.write(os.path.join(HERE, "README.txt"), "README.txt")
    print(f"{out}: {len(entries)} files, {len(downloads)} downloads, {os.path.getsize(out) // 1024} KB")


if __name__ == "__main__":
    main()
