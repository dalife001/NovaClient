#!/usr/bin/env python3
"""Works out which Minecraft versions to build Nova for and writes the GitHub Actions matrix.

Every stable version Fabric supports from 1.16.5 up to the newest one that still has Yarn mappings gets an entry, as long as a Yarn mapping and a
Fabric API build exist for it. Whether the code actually compiles on that version is decided by the build job.

Env: MIN_MC (default 1.16.5), ONLY_MC (comma separated list, to build just a few), GITHUB_OUTPUT.
"""
import json, os, re, sys, time, urllib.parse, urllib.request

FABRIC = "https://meta.fabricmc.net/v2"
MOJANG = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MODRINTH = "https://api.modrinth.com/v2"
UA = {"User-Agent": "NovaClient-ci/1.0 (github actions)"}


def get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.load(r)
        except Exception as e:  # network hiccup: wait and retry
            if i == tries - 1:
                raise
            time.sleep(2 * (i + 1))


def vkey(v):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def stable_versions(fabric_games, minimum):
    return sorted({g["version"] for g in fabric_games if g.get("stable") and re.fullmatch(r"\d+(\.\d+)+", g["version"])
                   and vkey(g["version"]) >= vkey(minimum)}, key=vkey, reverse=True)


def newest_yarn(entries):
    return max(entries, key=lambda e: e.get("build", 0))["version"] if entries else None


def pick_fabric_api(versions):
    for v in versions:
        if v.get("version_type") == "release":
            return v["version_number"]
    return versions[0]["version_number"] if versions else None


def loom_for(mc):
    """Newer Minecraft needs a newer Fabric Loom (the Gradle plugin). Older ones keep the version that is known to work."""
    v = vkey(mc)
    if v >= vkey("1.21.11"):
        return "1.14-SNAPSHOT"  # 1.11 chokes on the javadoc Fabric API ships for 1.21.11
    if v >= vkey("1.21.9"):
        return "1.11-SNAPSHOT"
    if v >= vkey("1.21.5"):
        return "1.10-SNAPSHOT"
    return "1.9-SNAPSHOT"


def gradle_for(mc):
    """Loom 1.14 needs Gradle 9; everything else stays on the Gradle that is known to work."""
    return "9.2.1" if vkey(mc) >= vkey("1.21.11") else "8.14.3"


def java_for(mojang_version_json):
    """Mojang's own answer; a version json without javaVersion is from the Java 8 era (up to 1.16)."""
    return int((mojang_version_json.get("javaVersion") or {}).get("majorVersion", 8))


def main():
    minimum = os.environ.get("MIN_MC", "1.16.5")
    only = [x.strip() for x in os.environ.get("ONLY_MC", "").split(",") if x.strip()]
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    tested = {l.strip() for l in open(os.path.join(root, "tools", "tested-versions.txt")) if l.strip() and not l.startswith("#")}

    versions = stable_versions(get(FABRIC + "/versions/game"), minimum)
    if only:
        versions = [v for v in versions if v in only]
    manifest = {v["id"]: v["url"] for v in get(MOJANG)["versions"]}

    include, skipped = [], []
    for mc in versions:
        try:
            yarn = newest_yarn(get(f"{FABRIC}/versions/yarn/{mc}"))
            q = f"{MODRINTH}/project/fabric-api/version?loaders=" + urllib.parse.quote('["fabric"]') + "&game_versions=" + urllib.parse.quote(f'["{mc}"]')
            api = pick_fabric_api(get(q))
            java = java_for(get(manifest[mc])) if mc in manifest else 8
        except Exception as e:
            skipped.append(f"{mc}: lookup failed ({e})")
            continue
        if not yarn or not api:
            skipped.append(f"{mc}: no {'yarn mapping' if not yarn else 'Fabric API build'}")
            continue
        include.append({"mc": mc, "yarn": yarn, "fabric_api": api, "java": java, "jdk": max(21, java),
                        "tested": mc in tested, "loom": loom_for(mc), "gradle": gradle_for(mc)})

    print(f"Building {len(include)} versions, skipping {len(skipped)}")
    for s in skipped:
        print("  skipped", s)
    if not include:
        sys.exit("No Minecraft versions to build")
    out = os.environ.get("GITHUB_OUTPUT")
    line = "matrix=" + json.dumps({"include": include}, separators=(",", ":"))
    if out:
        with open(out, "a") as f:
            f.write(line + "\n")
    else:
        print(line)


if __name__ == "__main__":
    main()
