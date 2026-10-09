#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lotos Studio: a local helper for the Lotos mod.

Runs a small web server on 127.0.0.1 only (nothing leaves this computer) and serves one page with:
  * the episode editor (storyboard: shots, camera, light, sound, text, references, confirm/redo marks),
  * scene and shader checks (compile, run on screenshots, before/after, scene frames),
  * the mod panel (build, copy to mods, git, disk space, logs).
Only python's standard library is used, so nothing has to be downloaded.
"""
from __future__ import annotations

import datetime
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

HERE = Path(__file__).resolve().parent
STATIC = HERE / "static"
DATA = HERE / "data"
PROJECT = HERE.parent.parent          # tools/lotos-studio -> repo root
for _d in ("episodes", "history", "refs", "checks"):
    (DATA / _d).mkdir(parents=True, exist_ok=True)

ID_RE = re.compile(r"^[a-z0-9_-]{1,40}$")
NO_WINDOW = 0x08000000 if os.name == "nt" else 0


# ------------------------------------------------------------------ config

def find_java(gradle_home: Path) -> str:
    cands = list(gradle_home.glob("jdks/*/bin/java.exe")) + list(gradle_home.glob("jdks/*/*/bin/java.exe"))
    for c in cands:
        if "17" in str(c):
            return str(c.parent.parent)
    if cands:
        return str(cands[0].parent.parent)
    return os.environ.get("JAVA_HOME", "")


def find_lwjgl(gradle_home: Path) -> list[str]:
    base = gradle_home / "caches" / "forge_gradle" / "maven_downloader" / "org" / "lwjgl"
    names = ["lwjgl-3.3.1.jar", "lwjgl-3.3.1-natives-windows.jar",
             "lwjgl-glfw-3.3.1.jar", "lwjgl-glfw-3.3.1-natives-windows.jar",
             "lwjgl-opengl-3.3.1.jar", "lwjgl-opengl-3.3.1-natives-windows.jar"]
    out = []
    for n in names:
        hit = list(base.rglob(n)) if base.exists() else []
        if hit:
            out.append(str(hit[0]))
    return out


def load_config() -> dict:
    gh = Path("D:/gradle-home")
    if not gh.exists():
        gh = Path.home() / ".gradle"
    cfg = {
        "project": str(PROJECT),
        "minecraft": str(Path(os.environ.get("APPDATA", str(Path.home()))) / ".minecraft"),
        "gradle_home": str(gh),
        "tmp": "D:/tmp" if Path("D:/").exists() else str(Path.home() / "tmp"),
        "java_home": "",
        "jar_name": "lotusblight-2.0008.jar",
        "port": 8765,
    }
    f = DATA / "config.json"
    if f.exists():
        try:
            cfg.update(json.loads(f.read_text(encoding="utf-8")))
        except Exception:
            pass
    if not cfg["java_home"]:
        cfg["java_home"] = find_java(Path(cfg["gradle_home"]))
    return cfg


CFG = load_config()


def build_env() -> dict:
    env = os.environ.copy()
    tmp = CFG["tmp"]
    Path(tmp).mkdir(parents=True, exist_ok=True)
    env["TEMP"] = env["TMP"] = tmp
    env["GRADLE_USER_HOME"] = CFG["gradle_home"]
    if CFG["java_home"]:
        env["JAVA_HOME"] = CFG["java_home"]
    env["JAVA_TOOL_OPTIONS"] = f"-Djava.io.tmpdir={tmp}"
    return env


def java_exe() -> str:
    jh = CFG["java_home"]
    return str(Path(jh) / "bin" / "java.exe") if jh else "java"


def status_report() -> dict:
    proj = Path(CFG["project"])
    mc = Path(CFG["minecraft"])
    lw = find_lwjgl(Path(CFG["gradle_home"]))
    return {
        "config": CFG,
        "checks": {
            "project": proj.exists(),
            "gradlew": (proj / "gradlew.bat").exists(),
            "minecraft": mc.exists(),
            "mods_dir": (mc / "mods").exists(),
            "java": bool(CFG["java_home"]) and Path(java_exe()).exists(),
            "lwjgl": len(lw) == 6,
            "shaderpack": (proj / "shaderpack" / "LotosCinema" / "shaders").exists(),
            "classes": (proj / "build" / "classes" / "java" / "main").exists(),
        },
    }


# ------------------------------------------------------------------ episodes

def new_shot(title="Новый кадр") -> dict:
    return {
        "id": uuid.uuid4().hex[:8], "title": title, "location": "", "characters": [], "action": "",
        "size": "Средний план", "angle": "На уровне глаз", "move": "Статика",
        "lightPreset": "Холодный фиолет (как 13-я концовка)", "keyColor": "#6a4cff", "fillColor": "#0a0818",
        "contrast": 70, "duration": 4, "sound": "", "text": "", "emotion": "", "transition": "Резкая склейка",
        "notes": "", "refs": [], "status": "draft", "comment": "",
    }


def default_episode(eid: str, title: str) -> dict:
    return {"id": eid, "title": title, "notes": "", "shots": [new_shot("Вступление")]}


def episode_path(eid: str) -> Path:
    return DATA / "episodes" / f"{eid}.json"


def load_episode(eid: str) -> dict:
    p = episode_path(eid)
    if p.exists():
        return json.loads(p.read_text(encoding="utf-8"))
    ep = default_episode(eid, "Эпизод 1" if eid == "episode1" else eid)
    p.write_text(json.dumps(ep, ensure_ascii=False, indent=2), encoding="utf-8")
    return ep


STATUS_MARK = {"draft": "✎ черновик", "ask": "? нужно подтвердить", "ok": "✔ подтверждено", "redo": "✖ переделать"}


def to_markdown(ep: dict) -> str:
    shots = ep.get("shots", [])
    total = sum(float(s.get("duration") or 0) for s in shots)
    lines = [f"# {ep.get('title', 'Эпизод')}", "",
             f"Кадров: {len(shots)}. Общая длительность: {total:g} с.", ""]
    if ep.get("notes"):
        lines += [ep["notes"], ""]
    for i, s in enumerate(shots, 1):
        lines += [f"## Кадр {i}. {s.get('title', '')}", "",
                  f"- Статус: {STATUS_MARK.get(s.get('status'), s.get('status', ''))}",
                  f"- Место: {s.get('location', '') or '—'}",
                  f"- В кадре: {', '.join(s.get('characters', [])) or '—'}",
                  f"- Что происходит: {s.get('action', '') or '—'}",
                  f"- Камера: {s.get('size', '')}, {s.get('angle', '')}, {s.get('move', '')}",
                  f"- Свет: {s.get('lightPreset', '')} (основной {s.get('keyColor', '')}, фон {s.get('fillColor', '')}, контраст {s.get('contrast', '')})",
                  f"- Эмоция: {s.get('emotion', '') or '—'}",
                  f"- Длительность: {s.get('duration', '')} с. Переход: {s.get('transition', '')}",
                  f"- Звук: {s.get('sound', '') or '—'}",
                  f"- Текст/субтитр: {s.get('text', '') or '—'}"]
        if s.get("notes"):
            lines.append(f"- Заметки: {s['notes']}")
        if s.get("comment"):
            lines.append(f"- Комментарий автора: {s['comment']}")
        if s.get("refs"):
            lines.append("- Референсы: " + ", ".join(f"refs/{ep['id']}/{r}" for r in s["refs"]))
        lines.append("")
    return "\n".join(lines)


def save_episode(eid: str, ep: dict) -> dict:
    ep["id"] = eid
    if not isinstance(ep.get("shots"), list):
        raise ValueError("shots must be a list")
    text = json.dumps(ep, ensure_ascii=False, indent=2)
    p = episode_path(eid)
    if p.exists() and p.read_text(encoding="utf-8") == text:
        return {"ok": True, "changed": False}
    if p.exists():
        stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(p, DATA / "history" / f"{eid}_{stamp}.json")
        old = sorted((DATA / "history").glob(f"{eid}_*.json"))
        for f in old[:-40]:
            f.unlink(missing_ok=True)
    p.write_text(text, encoding="utf-8")
    # export where the project (and Claude) can read it
    out = PROJECT / "art" / "episodes"
    (out / "refs" / eid).mkdir(parents=True, exist_ok=True)
    (out / f"{eid}.json").write_text(text, encoding="utf-8")
    (out / f"{eid}.md").write_text(to_markdown(ep), encoding="utf-8")
    for s in ep["shots"]:
        for r in s.get("refs", []):
            src = DATA / "refs" / Path(r).name
            if src.exists():
                shutil.copy2(src, out / "refs" / eid / Path(r).name)
    return {"ok": True, "changed": True, "exported": str(out / f"{eid}.md")}


def list_episodes() -> list:
    res = []
    for f in sorted((DATA / "episodes").glob("*.json")):
        try:
            ep = json.loads(f.read_text(encoding="utf-8"))
            res.append({"id": f.stem, "title": ep.get("title", f.stem), "shots": len(ep.get("shots", [])),
                        "duration": sum(float(s.get("duration") or 0) for s in ep.get("shots", []))})
        except Exception:
            continue
    if not res:
        load_episode("episode1")
        return list_episodes()
    return res


# ------------------------------------------------------------------ jobs

class Job:
    def __init__(self, kind: str, title: str):
        self.id = uuid.uuid4().hex[:10]
        self.kind = kind
        self.title = title
        self.lines: list[str] = []
        self.status = "running"
        self.images: list[dict] = []
        self.started = time.time()
        self.lock = threading.Lock()

    def log(self, s: str):
        with self.lock:
            self.lines.append(s)
            if len(self.lines) > 6000:
                del self.lines[:1000]

    def to_json(self, since: int = 0) -> dict:
        with self.lock:
            return {"id": self.id, "kind": self.kind, "title": self.title, "status": self.status,
                    "lines": self.lines[since:], "next": len(self.lines), "images": self.images,
                    "seconds": round(time.time() - self.started, 1)}


JOBS: dict[str, Job] = {}


def run_cmd(job: Job, args: list[str], cwd: str | None = None) -> int:
    job.log("$ " + " ".join(f'"{a}"' if " " in a else a for a in args))
    try:
        p = subprocess.Popen(args, cwd=cwd, env=build_env(), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             creationflags=NO_WINDOW)
    except Exception as e:  # noqa: BLE001
        job.log(f"Не удалось запустить: {e}")
        return 127
    assert p.stdout is not None
    for raw in iter(p.stdout.readline, b""):
        line = raw.decode("utf-8", errors="replace").rstrip()
        if "Picked up JAVA_TOOL_OPTIONS" in line or not line.strip():
            continue
        job.log(line)
    return p.wait()


def lwjgl_cp() -> str:
    return ";".join(find_lwjgl(Path(CFG["gradle_home"])))


def shaders_dir() -> Path:
    return Path(CFG["project"]) / "shaderpack" / "LotosCinema" / "shaders"


def job_disk(job: Job, p: dict) -> bool:
    for letter in "CD":
        root = f"{letter}:\\"
        if Path(root).exists():
            u = shutil.disk_usage(root)
            job.log(f"{letter}: свободно {u.free / 2**30:.1f} ГБ из {u.total / 2**30:.0f} ГБ ({100 * u.free / u.total:.0f}% свободно)")
            if u.free < 3 * 2**30:
                job.log(f"  ВНИМАНИЕ: на {letter}: мало места, лаунчер и сборка могут падать (нужно хотя бы 3 ГБ).")
    return True


def job_git_status(job: Job, p: dict) -> bool:
    proj = CFG["project"]
    a = run_cmd(job, ["git", "-C", proj, "status", "--short", "-b"])
    b = run_cmd(job, ["git", "-C", proj, "log", "--oneline", "-6"])
    return a == 0 and b == 0


ALLOWED_SCOPES = ["src", "shaderpack", "art", "tools", "build.gradle", "gradle.properties", "README.md"]


def job_git_commit(job: Job, p: dict) -> bool:
    proj = CFG["project"]
    msg = (p.get("message") or "").strip()
    if not msg:
        job.log("Нужно написать сообщение коммита.")
        return False
    scope = [s for s in p.get("scope", ALLOWED_SCOPES) if s in ALLOWED_SCOPES and (Path(proj) / s).exists()]
    if not scope:
        job.log("Нечего добавлять: не выбрана ни одна папка.")
        return False
    if run_cmd(job, ["git", "-C", proj, "add", "--"] + scope) != 0:
        return False
    rc = run_cmd(job, ["git", "-C", proj, "commit", "-m", msg])
    if rc != 0:
        job.log("Коммит не создан (возможно, нет изменений).")
        return False
    if p.get("push"):
        return run_cmd(job, ["git", "-C", proj, "push", "origin", "HEAD"]) == 0
    return True


def job_git_push(job: Job, p: dict) -> bool:
    return run_cmd(job, ["git", "-C", CFG["project"], "push", "origin", "HEAD"]) == 0


def job_build(job: Job, p: dict) -> bool:
    gw = str(Path(CFG["project"]) / "gradlew.bat")
    rc = run_cmd(job, ["cmd", "/c", gw, "build", "-q", "--no-daemon", "-x", "test"], cwd=CFG["project"])
    jar = Path(CFG["project"]) / "build" / "libs" / CFG["jar_name"]
    if jar.exists():
        job.log(f"Готово: {jar.name}, {jar.stat().st_size / 2**20:.1f} МБ, {datetime.datetime.fromtimestamp(jar.stat().st_mtime):%H:%M:%S}")
    return rc == 0 and jar.exists()


def job_copy_jar(job: Job, p: dict) -> bool:
    src = Path(CFG["project"]) / "build" / "libs" / CFG["jar_name"]
    dst = Path(CFG["minecraft"]) / "mods" / CFG["jar_name"]
    if not src.exists():
        job.log("Сначала собери мод: файла нет в build/libs.")
        return False
    try:
        shutil.copy2(src, dst)
    except PermissionError:
        job.log("Файл занят: закрой Minecraft и повтори.")
        return False
    job.log(f"Скопировано в {dst} ({dst.stat().st_size / 2**20:.1f} МБ)")
    return True


def job_build_copy(job: Job, p: dict) -> bool:
    return job_build(job, p) and job_copy_jar(job, p)


def job_shader_compile(job: Job, p: dict) -> bool:
    tool = Path(CFG["project"]) / "shaderpack" / "tools" / "CompileAll.java"
    if not CFG["java_home"] or not lwjgl_cp():
        job.log("Не найдены Java или LWJGL (см. вкладку «Состояние»).")
        return False
    rc = run_cmd(job, [java_exe(), "-cp", lwjgl_cp(), str(tool), str(shaders_dir())])
    return rc == 0 and any("ALL OK" in ln for ln in job.lines)


def job_shader_run(job: Job, p: dict) -> bool:
    tool = Path(CFG["project"]) / "shaderpack" / "tools" / "ShaderCheck.java"
    shots_dir = Path(CFG["minecraft"]) / "screenshots"
    names = [Path(n).name for n in p.get("names", [])]
    if not names:
        names = [f.name for f in sorted(shots_dir.glob("*.png"), key=lambda f: f.stat().st_mtime)[-2:]]
    if not names:
        job.log("Скриншотов нет: сделай F2 в игре.")
        return False
    out = DATA / "checks" / job.id
    out.mkdir(parents=True, exist_ok=True)
    ok = True
    for i, n in enumerate(names):
        src = shots_dir / n
        if not src.exists():
            job.log(f"Нет файла {n}")
            ok = False
            continue
        before, after = out / f"before_{i}.png", out / f"after_{i}.png"
        shutil.copy2(src, before)
        rc = run_cmd(job, [java_exe(), "-cp", lwjgl_cp(), str(tool), str(shaders_dir()), str(src), str(after)])
        if rc == 0 and after.exists():
            job.images.append({"name": n, "before": f"/checks/{job.id}/before_{i}.png", "after": f"/checks/{job.id}/after_{i}.png"})
        else:
            ok = False
    return ok


def job_shader_install(job: Job, p: dict) -> bool:
    src = Path(CFG["project"]) / "shaderpack" / "LotosCinema"
    dst = Path(CFG["minecraft"]) / "shaderpacks" / "LotosCinema"
    shutil.copytree(src, dst, dirs_exist_ok=True)
    job.log(f"Пак скопирован в {dst}")
    job.log("В игре: Видео → Шейдеры → LotosCinema. F3+R перезагружает шейдеры без перезапуска.")
    return True


def job_scene_frames(job: Job, p: dict) -> bool:
    proj = Path(CFG["project"])
    classes = proj / "build" / "classes" / "java" / "main"
    if not classes.exists():
        job.log("Нет скомпилированных классов: сначала собери мод.")
        return False
    times = [t for t in re.findall(r"\d+(?:\.\d+)?", str(p.get("times", ""))) ][:12]
    if not times:
        job.log("Укажи времена в секундах, например: 83, 100.5, 120")
        return False
    out = DATA / "checks" / job.id
    out.mkdir(parents=True, exist_ok=True)
    skin = proj / "src/main/resources/assets/lotusblight/textures/entity/honcho.png"
    beats = proj / "src/main/resources/assets/lotusblight/ending13/beats.json"
    cp = f"{classes};{proj / 'build' / 'resources' / 'main'}"
    rc = run_cmd(job, [java_exe(), "-Djava.awt.headless=true", "-cp", cp, "com.lotusblight.client.Ending13Scene",
                       str(out), str(skin), str(beats)] + times)
    for f in sorted(out.glob("t*.png")):
        job.images.append({"name": f.stem, "after": f"/checks/{job.id}/{f.name}"})
    return rc == 0 and bool(job.images)


JOB_KINDS = {
    "disk": ("Свободное место", job_disk),
    "git_status": ("Git: состояние", job_git_status),
    "git_commit": ("Git: коммит", job_git_commit),
    "git_push": ("Git: отправка", job_git_push),
    "build": ("Сборка мода", job_build),
    "copy_jar": ("Копирование в mods", job_copy_jar),
    "build_copy": ("Сборка и копирование", job_build_copy),
    "shader_compile": ("Компиляция шейдера", job_shader_compile),
    "shader_run": ("Шейдер на скриншотах", job_shader_run),
    "shader_install": ("Установка пака в игру", job_shader_install),
    "scene_frames": ("Кадры сцены 13", job_scene_frames),
}


def start_job(kind: str, params: dict) -> Job:
    title, fn = JOB_KINDS[kind]
    job = Job(kind, title)
    JOBS[job.id] = job
    for old in sorted(JOBS.values(), key=lambda j: j.started)[:-30]:
        JOBS.pop(old.id, None)

    def runner():
        try:
            ok = fn(job, params)
        except Exception as e:  # noqa: BLE001
            job.log(f"Ошибка: {e}")
            ok = False
        job.log("— готово —" if ok else "— завершено с ошибкой —")
        job.status = "ok" if ok else "fail"

    threading.Thread(target=runner, daemon=True).start()
    return job


# ------------------------------------------------------------------ logs and screenshots

def tail_text(path: Path, lines: int = 400) -> str:
    size = path.stat().st_size
    with path.open("rb") as f:
        f.seek(max(0, size - 400_000))
        data = f.read()
    if size > 400_000 and b"\n" in data:
        data = data[data.index(b"\n") + 1:]          # drop the line the seek cut in half
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("cp1251", errors="replace")   # the game writes the log in the system code page
    return "\n".join(text.splitlines()[-lines:])


def read_log(name: str) -> dict:
    mc = Path(CFG["minecraft"])
    if name == "crash":
        cands = list(mc.glob("hs_err_pid*.log")) + list((mc / "crash-reports").glob("*.txt"))
        if not cands:
            return {"path": "", "text": "Отчётов о вылете нет."}
        path = max(cands, key=lambda f: f.stat().st_mtime)
    elif name == "debug":
        path = mc / "logs" / "debug.log"
    elif name == "launcher":
        path = mc / "launcher_log3.txt"
    else:
        path = mc / "logs" / "latest.log"
    if not path.exists():
        return {"path": str(path), "text": "Файла нет."}
    return {"path": str(path), "mtime": datetime.datetime.fromtimestamp(path.stat().st_mtime).strftime("%d.%m %H:%M:%S"),
            "text": tail_text(path)}


def list_screenshots() -> list:
    d = Path(CFG["minecraft"]) / "screenshots"
    if not d.exists():
        return []
    files = sorted(d.glob("*.png"), key=lambda f: f.stat().st_mtime, reverse=True)[:30]
    return [{"name": f.name, "mb": round(f.stat().st_size / 2**20, 1),
             "time": datetime.datetime.fromtimestamp(f.stat().st_mtime).strftime("%d.%m %H:%M")} for f in files]


# ------------------------------------------------------------------ http

class Handler(BaseHTTPRequestHandler):
    server_version = "LotosStudio"

    def log_message(self, *a):  # keep the console quiet
        pass

    def host_ok(self) -> bool:
        return self.headers.get("Host", "").split(":")[0] in ("127.0.0.1", "localhost")

    def send_bytes(self, data: bytes, ctype: str, code: int = 200):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, obj, code: int = 200):
        self.send_bytes(json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8", code)

    def fail(self, code: int, msg: str):
        self.send_bytes(msg.encode("utf-8"), "text/plain; charset=utf-8", code)

    def serve_file(self, base: Path, rel: str):
        p = (base / rel).resolve()
        try:
            p.relative_to(base.resolve())
        except ValueError:
            return self.fail(403, "forbidden")
        if not p.is_file():
            return self.fail(404, "not found")
        ctype = mimetypes.guess_type(str(p))[0] or "application/octet-stream"
        if p.suffix in (".html", ".js", ".css", ".json", ".md", ".txt"):
            ctype += "; charset=utf-8"
        self.send_bytes(p.read_bytes(), ctype)

    def body(self, limit: int = 3_000_000) -> bytes:
        n = int(self.headers.get("Content-Length", "0") or 0)
        if n > limit:
            raise ValueError("слишком большой запрос")
        return self.rfile.read(n)

    def do_GET(self):
        if not self.host_ok():
            return self.fail(403, "bad host")
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = unquote(u.path)
        try:
            if path in ("/", "/index.html"):
                return self.serve_file(STATIC, "index.html")
            if path in ("/app.js", "/style.css"):
                return self.serve_file(STATIC, path[1:])
            if path == "/api/status":
                return self.send_json(status_report())
            if path == "/api/episodes":
                return self.send_json(list_episodes())
            if path == "/api/episode":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                return self.send_json(load_episode(eid))
            if path == "/api/history":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                files = sorted((DATA / "history").glob(f"{eid}_*.json"), reverse=True)[:40]
                return self.send_json([f.name for f in files])
            if path == "/api/history_get":
                name = Path(q.get("file", "")).name
                return self.serve_file(DATA / "history", name)
            if path == "/api/job":
                job = JOBS.get(q.get("id", ""))
                if not job:
                    return self.fail(404, "no such job")
                return self.send_json(job.to_json(int(q.get("since", "0") or 0)))
            if path == "/api/log":
                return self.send_json(read_log(q.get("name", "latest")))
            if path == "/api/screenshots":
                return self.send_json(list_screenshots())
            if path.startswith("/refs/"):
                return self.serve_file(DATA / "refs", path[6:])
            if path.startswith("/checks/"):
                return self.serve_file(DATA / "checks", path[8:])
            if path.startswith("/mcshots/"):
                return self.serve_file(Path(CFG["minecraft"]) / "screenshots", path[9:])
            return self.fail(404, "not found")
        except Exception as e:  # noqa: BLE001
            return self.fail(500, f"{type(e).__name__}: {e}")

    def do_POST(self):
        if not self.host_ok():
            return self.fail(403, "bad host")
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = unquote(u.path)
        try:
            if path == "/api/episode":
                eid = q.get("id", "episode1")
                if not ID_RE.match(eid):
                    return self.fail(400, "bad id")
                return self.send_json(save_episode(eid, json.loads(self.body().decode("utf-8"))))
            if path == "/api/episode_new":
                title = (json.loads(self.body().decode("utf-8")).get("title") or "Новый эпизод").strip()[:80]
                n = 1
                while episode_path(f"episode{n}").exists():
                    n += 1
                eid = f"episode{n}"
                ep = default_episode(eid, title)
                episode_path(eid).write_text(json.dumps(ep, ensure_ascii=False, indent=2), encoding="utf-8")
                return self.send_json({"id": eid})
            if path == "/api/upload":
                name = re.sub(r"[^A-Za-z0-9._-]", "_", q.get("name", "ref.png"))[-60:]
                ext = Path(name).suffix.lower()
                if ext not in (".png", ".jpg", ".jpeg", ".webp", ".gif"):
                    return self.fail(400, "only images")
                fname = f"{uuid.uuid4().hex[:8]}_{name}"
                (DATA / "refs" / fname).write_bytes(self.body(15_000_000))
                return self.send_json({"file": fname})
            if path == "/api/job":
                req = json.loads(self.body().decode("utf-8"))
                kind = req.get("kind", "")
                if kind not in JOB_KINDS:
                    return self.fail(400, "unknown job")
                return self.send_json({"id": start_job(kind, req.get("params", {})).id})
            return self.fail(404, "not found")
        except Exception as e:  # noqa: BLE001
            return self.fail(500, f"{type(e).__name__}: {e}")


def main():
    port = int(CFG["port"])
    try:
        srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    except OSError as e:
        print(f"Не удалось занять порт {port}: {e}\nВозможно, Lotos Studio уже запущена: открой http://127.0.0.1:{port}")
        sys.exit(1)
    url = f"http://127.0.0.1:{port}"
    print(f"Lotos Studio: {url}\nПроект: {CFG['project']}\nЗакрой это окно, чтобы остановить.")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
