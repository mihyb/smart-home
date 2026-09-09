#!/usr/bin/env python3
"""Convert openHAB JSONDB Thing definitions into file-based .things syntax.

The UI stores Things in /var/lib/openhab/jsondb/org.openhab.core.thing.Thing.json,
which is server-only state and does not survive a rebuild. This renders the same
Things as text so they live in git and deploy with the rest of the config.

Usage:
    python3 scripts/jsondb-to-things.py <Thing.json> <output-dir>

Secret-bearing configuration (Tuya credentials, device local keys) is replaced
with placeholders and written to a .example file; the real values belong in a
gitignored file restored from the password manager. See SECRET_KEYS below.
"""

import json
import sys
from collections import defaultdict
from pathlib import Path

# Config keys whose values must never reach git.
SECRET_KEYS = {"accessSecret", "password", "localKey", "accessId", "username"}

# Config values equal to these are openHAB defaults; emitting them adds noise.
SKIP_EMPTY = ("", None, [], {})

PLACEHOLDER = "<<SET_ME>>"


def esc(value: str) -> str:
    return str(value).replace("\\", "\\\\").replace('"', '\\"')


def render_value(key: str, value, redact: bool) -> str:
    """Render one config value in .things syntax."""
    if redact and key in SECRET_KEYS:
        return f'"{PLACEHOLDER}"'
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, list):
        return ", ".join(render_value(key, v, redact) for v in value)
    return f'"{esc(value)}"'


def render_config(config: dict, redact: bool, indent: str) -> str:
    """Render a [ key=value, ... ] config block, or '' when there is nothing to say."""
    items = [
        f"{k}={render_value(k, v, redact)}"
        for k, v in sorted(config.items())
        if v not in SKIP_EMPTY
    ]
    if not items:
        return ""
    if len(items) <= 3:
        return " [ " + ", ".join(items) + " ]"
    joined = f",\n{indent}    ".join(items)
    return f" [\n{indent}    {joined}\n{indent}]"


def config_of(thing: dict) -> dict:
    cfg = thing.get("configuration") or {}
    # openHAB nests real config under .properties in some schema versions
    if isinstance(cfg, dict) and "properties" in cfg and isinstance(cfg["properties"], dict):
        return cfg["properties"]
    return cfg if isinstance(cfg, dict) else {}


def channel_type(channel: dict) -> str:
    """'mqtt:number' -> 'number'; a full UID stays qualified."""
    ctype = channel.get("channelTypeUID", "")
    parts = ctype.split(":")
    return parts[-1] if len(parts) == 2 else ctype


def render_channels(channels: list, redact: bool, indent: str) -> list[str]:
    if not channels:
        return []
    lines = [f"{indent}Channels:"]
    for ch in channels:
        cfg = render_config(ch.get("configuration") or {}, redact, indent + "    ")
        label = ch.get("label") or ch["id"]
        lines.append(
            f'{indent}    Type {channel_type(ch)} : {ch["id"]} "{esc(label)}"{cfg}'
        )
    return lines


def thing_id(uid: str, bridge_uid: str | None) -> str:
    """The trailing segment(s) that identify a Thing beneath its bridge."""
    if bridge_uid and uid.startswith(bridge_uid + ":"):
        return uid[len(bridge_uid) + 1:]
    return uid.split(":")[-1]


def render_thing(thing: dict, redact: bool, nested: bool) -> list[str]:
    indent = "    " if nested else ""
    uid = thing["UID"]
    type_uid = thing["thingTypeUID"]
    label = thing.get("label") or uid
    cfg = render_config(config_of(thing), redact, indent)

    if nested:
        # Inside a Bridge block the binding prefix is implied.
        head = f'{indent}Thing {type_uid.split(":")[-1]} {thing_id(uid, thing.get("bridgeUID"))} "{esc(label)}"{cfg}'
    else:
        head = f'{indent}Thing {uid} "{esc(label)}"{cfg}'

    channels = render_channels(thing.get("channels") or [], redact, indent + "    ")
    if not channels:
        return [head]
    return [head + " {", *channels, f"{indent}}}"]


def render_bridge(bridge: dict, children: list, redact: bool) -> list[str]:
    uid = bridge["UID"]
    label = bridge.get("label") or uid
    cfg = render_config(config_of(bridge), redact, "")
    lines = [f'Bridge {uid} "{esc(label)}"{cfg} {{']
    for child in sorted(children, key=lambda t: t["UID"]):
        lines += render_thing(child, redact, nested=True)
        lines.append("")
    if lines[-1] == "":
        lines.pop()
    lines.append("}")
    return lines


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    raw = json.loads(Path(sys.argv[1]).read_text())
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    things = [entry["value"] for entry in raw.values() if entry.get("value")]

    by_binding: dict[str, list] = defaultdict(list)
    for thing in things:
        by_binding[thing["UID"].split(":")[0]].append(thing)

    written = []
    for binding, group in sorted(by_binding.items()):
        # A binding whose config carries secrets is rendered redacted, to .example
        redact = any(
            k in SECRET_KEYS
            for t in group
            for k in config_of(t)
        )

        bridges = {t["UID"]: t for t in group if t.get("isBridge")}
        children_of: dict[str, list] = defaultdict(list)
        standalone = []
        for thing in group:
            if thing.get("isBridge"):
                continue
            parent = thing.get("bridgeUID")
            if parent in bridges:
                children_of[parent].append(thing)
            else:
                standalone.append(thing)

        lines = [
            f"// {binding} Things — generated from JSONDB by scripts/jsondb-to-things.py",
            "// Edit this file, not the UI: file-defined Things are read-only in the UI",
            "// and are what makes this config reproducible on a new machine.",
            "",
        ]
        if redact:
            lines.insert(3, f"// Secrets replaced with {PLACEHOLDER}. Copy to {binding}.things")
            lines.insert(4, "// and fill in from the password manager; that file is gitignored.")

        for uid in sorted(bridges):
            lines += render_bridge(bridges[uid], children_of.get(uid, []), redact)
            lines.append("")
        for thing in sorted(standalone, key=lambda t: t["UID"]):
            lines += render_thing(thing, redact, nested=False)
            lines.append("")

        name = f"{binding}.things.example" if redact else f"{binding}.things"
        path = out_dir / name
        path.write_text("\n".join(lines).rstrip() + "\n")
        written.append((name, len(group), redact))

    for name, count, redact in written:
        flag = "  (secrets redacted)" if redact else ""
        print(f"  {name}: {count} things{flag}")
    print(f"\n{sum(c for _, c, _ in written)} things -> {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
