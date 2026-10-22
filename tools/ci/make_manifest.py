#!/usr/bin/env python3
"""Turns the jars the build jobs produced into release files + latest.json.

usage: make_manifest.py BUILDS_DIR OUT_DIR VERSION BASE_URL LAUNCHER_VERSION [TESTED_FILE]
BUILDS_DIR holds one folder per successful build, named build-<minecraft version>, each with one novaclient jar.
"""
import glob, hashlib, json, os, re, shutil, sys


def vkey(v):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def build_manifest(found, version, base_url, launcher_version, tested):
    """found: {mc: (file_name, sha256)}. The default version is the newest tested one, else the newest built."""
    builds = {}
    for mc in sorted(found, key=vkey, reverse=True):
        name, digest = found[mc]
        builds[mc] = {"url": f"{base_url}/{name}", "sha256": digest, "tested": mc in tested}
    candidates = [m for m in builds if m in tested] or list(builds)
    default = max(candidates, key=vkey)
    return {
        "version": version,
        "minecraft": default,
        "mod": {"url": builds[default]["url"], "sha256": builds[default]["sha256"]},  # for launchers that only know one build
        "builds": builds,
        "launcher": {"version": launcher_version, "url": f"{base_url}/NovaLauncher.jar"},
    }


def main():
    builds_dir, out_dir, version, base_url, launcher_version = sys.argv[1:6]
    tested = set()
    if len(sys.argv) > 6 and os.path.exists(sys.argv[6]):
        tested = {l.strip() for l in open(sys.argv[6]) if l.strip() and not l.startswith("#")}
    os.makedirs(out_dir, exist_ok=True)
    found = {}
    for d in glob.glob(os.path.join(builds_dir, "build-*")):
        mc = os.path.basename(d)[len("build-"):]
        jars = [j for j in glob.glob(os.path.join(d, "novaclient-*.jar")) if not re.search(r"-(sources|dev)\.jar$", j)]
        if not jars:
            continue
        name = f"novaclient-{version}-mc{mc}.jar"
        shutil.copy(jars[0], os.path.join(out_dir, name))
        found[mc] = (name, sha256(jars[0]))
    if not found:
        sys.exit("No build succeeded, nothing to publish")
    manifest = build_manifest(found, version, base_url, launcher_version, tested)
    with open(os.path.join(out_dir, "latest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"Published {len(found)} builds, default Minecraft {manifest['minecraft']}: {', '.join(sorted(found, key=vkey, reverse=True))}")


if __name__ == "__main__":
    main()
