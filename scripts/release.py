#!/usr/bin/env python3
"""Checks the built jars and copies them to release/: scripts/release.py "what they were tested with"

Run after ./gradlew build, on a committed tree: the name carries the commit. Older files in release/ stay. Fails without copying
anything when a jar's metadata is wrong, a mixin class is missing, or a class differs from the jars last installed in
test-instance/full, the ones the tests ran on.
"""
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent


def newest(directory, pattern):
    return sorted(directory.glob(pattern), key=lambda path: path.stat().st_mtime)[-1]


def properties():
    values = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def check(jar, mod_id, version, mixin_config, needed_files, forbidden=()):
    problems = []
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        toml = archive.read("META-INF/neoforge.mods.toml").decode()
        for text in (f'modId="{mod_id}"', f'version="{version}"', 'license="LGPL-3.0-only"'):
            if text not in toml:
                problems.append(f"{mod_id}: no {text}")
        problems += [f"{mod_id}: unexpanded {found}" for found in re.findall(r"\$\{[^}]+\}", toml)]
        config = json.loads(archive.read(mixin_config))
        package = config["package"].replace(".", "/")
        problems += [f"{mod_id}: no mixin class {mixin}" for mixin in config["client"] if f"{package}/{mixin}.class" not in names]
        problems += [f"{mod_id}: no {name}" for name in needed_files if name not in names]
        problems += [f"{mod_id}: must not ship {name}" for name in names for prefix in forbidden if name.startswith(prefix)]
    return problems


def same_classes(jar, tested):
    """How many classes the two jars share, and those that differ."""
    with zipfile.ZipFile(jar) as a, zipfile.ZipFile(tested) as b:
        ours = {i.filename: i.CRC for i in a.infolist() if i.filename.endswith(".class")}
        theirs = {i.filename: i.CRC for i in b.infolist() if i.filename.endswith(".class")}
    different = sorted(name for name in set(ours) | set(theirs) if ours.get(name) != theirs.get(name))
    return len(ours), different


def main():
    tested_with = sys.argv[1] if len(sys.argv) > 1 else ""
    values = properties()
    commit = subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"], text=True).strip()
    if subprocess.check_output(["git", "-C", str(ROOT), "status", "--porcelain"], text=True).strip():
        sys.exit("uncommitted changes: the name would lie about the commit")
    vitrail = ROOT / "build" / "libs" / f"vitrailvk-{values['mod_version']}.jar"
    bridge = ROOT / "bridge" / "build" / "libs" / f"vitrailbridge-{values['bridge_version']}.jar"
    problems = check(vitrail, "vitrailvk", values["mod_version"], "vitrailvk.mixins.json",
                     ["LICENSE_vitrailvk", "COPYING_vitrailvk", "NOTICE_vitrailvk", "NOTICE-vitrail_vitrailvk", "assets/vitrailvk/icon.png"])
    problems += check(bridge, "vitrailbridge", values["bridge_version"], "vitrailbridge.mixins.json",
                      ["LICENSE_vitrailbridge", "COPYING_vitrailbridge", "NOTICE_vitrailbridge"], forbidden=("dev/opal/", "dev/vector/"))
    with zipfile.ZipFile(vitrail) as archive:
        wanted = f'versionRange="[{values["vulkanmod_version"]}]"'
        if wanted not in archive.read("META-INF/neoforge.mods.toml").decode():
            problems.append(f"vitrailvk: no {wanted}")
    mods = ROOT / "test-instance" / "full" / "mods"
    for jar, pattern in ((vitrail, "vitrailvk-*.jar"), (bridge, "vitrailbridge-*.jar")):
        count, different = same_classes(jar, newest(mods, pattern))
        print(f"{jar.name}: {count} classes, {len(different)} differ from the jar the tests ran on")
        problems += [f"{jar.name}: differs from what was tested: {name}" for name in different]
    if problems:
        sys.exit("\n".join(problems))

    release = ROOT / "release"
    release.mkdir(exist_ok=True)
    lines = []
    for jar, stem, version in ((vitrail, "vitrailvk", values["mod_version"]), (bridge, "vitrailbridge", values["bridge_version"])):
        name = f"{stem}-{version}-{commit}.jar"
        shutil.copy2(jar, release / name)
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        lines += [name, f"sha256 {digest}"]
        print(name, digest)
    lines.append(f"built from {commit}. {tested_with}")
    (release / "LATEST.txt").write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
