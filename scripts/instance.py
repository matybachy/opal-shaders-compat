#!/usr/bin/env python3
"""A clean NeoForge client outside the dev run, for testing the built jar.

  scripts/instance.py create NAME          install NeoForge into test-instance/NAME
  scripts/instance.py run NAME [options]   start it with what is in its mods/ folder

Options of run:
  --pack NAME      shader pack (vitrailvk.pack)
  --options A=b,C=d
  --script FILE    an autotest script (see AutoTest); without one, the plain autotest
  --camera x,z,yaw,pitch,height
  --time N
  --timeout SECONDS
  --no-validation  without the Khronos layer
  --no-autotest    just start the game
  --jvm ARG        one more JVM argument, repeatable

Output goes to test-instance/NAME/autotest. The Java is Zulu 21 from JAVA_HOME or the usual place.
"""
import argparse
import json
import os
import pathlib
import platform
import subprocess
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
NEOFORGE = "21.1.228"
MINECRAFT = "1.21.1"
JAVA_HOME = pathlib.Path(os.environ.get("VITRAILVK_JAVA", r"C:\Program Files\Zulu\zulu-21"))
JAVA = JAVA_HOME / "bin" / ("java.exe" if os.name == "nt" else "java")


def instance(name):
    return ROOT / "test-instance" / name


def create(name):
    home = instance(name)
    home.mkdir(parents=True, exist_ok=True)
    installer = ROOT / "test-instance" / f"neoforge-{NEOFORGE}-installer.jar"
    if not installer.exists():
        url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE}/neoforge-{NEOFORGE}-installer.jar"
        print("downloading", url)
        installer.write_bytes(urllib.request.urlopen(url, timeout=300).read())
    profiles = home / "launcher_profiles.json"
    if not profiles.exists():
        profiles.write_text('{"profiles":{}}')
    subprocess.check_call([str(JAVA), "-jar", str(installer), "--install-client", str(home)])
    (home / "mods").mkdir(exist_ok=True)
    print("installed in", home)


def library_path(home, entry):
    if "downloads" in entry and entry["downloads"].get("artifact"):
        return home / "libraries" / entry["downloads"]["artifact"]["path"]
    group, artifact, version, *rest = entry["name"].split(":")
    classifier = f"-{rest[0]}" if rest else ""
    return home / "libraries" / group.replace(".", "/") / artifact / version / f"{artifact}-{version}{classifier}.jar"


def allowed(entry):
    for rule in entry.get("rules", []):
        os_rule = rule.get("os", {})
        matches = not os_rule or os_rule.get("name") == {"Windows": "windows", "Linux": "linux", "Darwin": "osx"}[platform.system()]
        if rule["action"] == "allow" and not matches:
            return False
        if rule["action"] == "disallow" and matches:
            return False
    return True


def fetch_missing(home, version):
    for entry in version["libraries"]:
        if not allowed(entry):
            continue
        path = library_path(home, entry)
        artifact = entry.get("downloads", {}).get("artifact")
        if not path.exists() and artifact and artifact.get("url"):
            path.parent.mkdir(parents=True, exist_ok=True)
            print("downloading", artifact["url"])
            path.write_bytes(urllib.request.urlopen(artifact["url"], timeout=300).read())


def fetch_assets(home, vanilla):
    index = vanilla["assetIndex"]
    index_file = home / "assets" / "indexes" / f"{index['id']}.json"
    if not index_file.exists():
        index_file.parent.mkdir(parents=True, exist_ok=True)
        index_file.write_bytes(urllib.request.urlopen(index["url"], timeout=300).read())
    objects = json.loads(index_file.read_text())["objects"]
    missing = []
    for value in objects.values():
        hash_ = value["hash"]
        path = home / "assets" / "objects" / hash_[:2] / hash_
        if not path.exists():
            missing.append((hash_, path))
    if not missing:
        return index["id"]
    print(len(missing), "assets to fetch")
    from concurrent.futures import ThreadPoolExecutor

    def one(item):
        hash_, path = item
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(urllib.request.urlopen(f"https://resources.download.minecraft.net/{hash_[:2]}/{hash_}", timeout=300).read())

    with ThreadPoolExecutor(16) as pool:
        list(pool.map(one, missing))
    return index["id"]


def run(name, args):
    home = instance(name)
    neoforge = json.loads((home / "versions" / f"neoforge-{NEOFORGE}" / f"neoforge-{NEOFORGE}.json").read_text())
    vanilla_file = home / "versions" / MINECRAFT / f"{MINECRAFT}.json"
    vanilla = json.loads(vanilla_file.read_text())
    fetch_missing(home, neoforge)
    fetch_missing(home, vanilla)
    client = home / "versions" / MINECRAFT / f"{MINECRAFT}.jar"
    if not client.exists():
        client.write_bytes(urllib.request.urlopen(vanilla["downloads"]["client"]["url"], timeout=300).read())
    asset_index = fetch_assets(home, vanilla)

    separator = os.pathsep
    seen = {}
    for version in (vanilla, neoforge):
        for entry in version["libraries"]:
            if allowed(entry):
                # A later entry replaces the same library of another version, as the launcher does.
                seen[":".join(entry["name"].split(":")[:2] + entry["name"].split(":")[3:])] = library_path(home, entry)
    classpath = list(seen.values()) + [client]
    substitutions = {
        "${library_directory}": str(home / "libraries"),
        "${classpath_separator}": separator,
        "${classpath}": separator.join(str(p) for p in classpath),
        "${version_name}": f"neoforge-{NEOFORGE}",
        "${natives_directory}": str(home / "natives"),
        "${launcher_name}": "vitrailvk-test",
        "${launcher_version}": "1",
        "${auth_player_name}": "Tester",
        "${game_directory}": str(home),
        "${assets_root}": str(home / "assets"),
        "${assets_index_name}": asset_index,
        "${auth_uuid}": "00000000000000000000000000000000",
        "${auth_access_token}": "0",
        "${clientid}": "0",
        "${auth_xuid}": "0",
        "${user_type}": "legacy",
        "${version_type}": "release",
    }

    def fill(items):
        result = []
        for item in items:
            if isinstance(item, dict):
                if not allowed(item) or item.get("rules") and any("features" in rule for rule in item["rules"]):
                    continue
                value = item["value"]
                values = value if isinstance(value, list) else [value]
            else:
                values = [item]
            for value in values:
                for key, replacement in substitutions.items():
                    value = value.replace(key, replacement)
                result.append(value)
        return result

    (home / "natives").mkdir(exist_ok=True)
    out = home / "autotest"
    out.mkdir(exist_ok=True)
    jvm = ["-Xmx6G", "-XX:+UseG1GC"]
    env = dict(os.environ)
    if not args.no_autotest:
        jvm += ["-Dvitrailvk.autotest=true", f"-Dvitrailvk.autotest.out={out}"]
        jvm.append(f"-Dvitrailvk.autotest.camera={args.camera}")
        jvm.append(f"-Dvitrailvk.autotest.time={args.time}")
        jvm.append(f"-Dvitrailvk.autotest.timeout={args.timeout}")
        if args.script:
            jvm.append(f"-Dvitrailvk.autotest.script={pathlib.Path(args.script).resolve()}")
    if args.pack is not None:
        jvm.append(f"-Dvitrailvk.pack={args.pack}")
    if args.options:
        jvm.append(f"-Dvitrailvk.options={args.options}")
    jvm += args.jvm or []
    validation = ROOT / "tools" / "vvl"
    log = out / "validation.log"
    if log.exists():
        log.unlink()
    if not args.no_validation and (validation / "VkLayer_khronos_validation.json").exists():
        env.update({
            "VK_ADD_LAYER_PATH": str(validation),
            "VK_INSTANCE_LAYERS": "VK_LAYER_KHRONOS_validation",
            "VK_KHRONOS_VALIDATION_DEBUG_ACTION": "VK_DBG_LAYER_ACTION_LOG_MSG",
            "VK_KHRONOS_VALIDATION_REPORT_FLAGS": "error,warn",
            "VK_KHRONOS_VALIDATION_LOG_FILENAME": str(log),
        })
    command = [str(JAVA)] + jvm + fill(vanilla["arguments"]["jvm"]) + fill(neoforge["arguments"]["jvm"])
    command += [neoforge["mainClass"]] + fill(vanilla["arguments"]["game"]) + fill(neoforge["arguments"]["game"])
    # The launcher hands the vanilla arguments that take a value in pairs; a feature flag lost its value with fill().
    (out / "command.txt").write_text("\n".join(command))
    console = open(out / "console.log", "w", encoding="utf-8")
    code = subprocess.call(command, cwd=home, env=env, stdout=console, stderr=subprocess.STDOUT)
    print("exit code", code)
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["create", "run"])
    parser.add_argument("name")
    parser.add_argument("--pack")
    parser.add_argument("--options", default="")
    parser.add_argument("--script")
    parser.add_argument("--camera", default="0,0,135,20,14")
    parser.add_argument("--time", default="6000")
    parser.add_argument("--timeout", default="600")
    parser.add_argument("--no-validation", action="store_true")
    parser.add_argument("--no-autotest", action="store_true")
    parser.add_argument("--jvm", action="append")
    args = parser.parse_args()
    if args.action == "create":
        create(args.name)
    else:
        sys.exit(run(args.name, args))


if __name__ == "__main__":
    main()
