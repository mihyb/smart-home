#!/usr/bin/env python3
"""Convert openHAB JSONDB Items + ItemChannelLinks into file-based .items syntax.

The UI stores items in org.openhab.core.items.Item.json and their channel
bindings in org.openhab.core.thing.link.ItemChannelLink.json — both server-only
state. Rendering them as text puts the link inline in the item definition, which
is how the existing file-based items already work, and makes ItemChannelLink.json
unnecessary.

Usage:
    python3 scripts/jsondb-to-items.py <Item.json> <ItemChannelLink.json> <out.items>
"""

import json
import sys
from collections import defaultdict
from pathlib import Path


def esc(value: str) -> str:
    return str(value).replace("\\", "\\\\").replace('"', '\\"')


def render_item(name: str, item: dict, channels: list[str]) -> str:
    parts = [item.get("itemType", "String")]

    # Group items carry their aggregation in the type, e.g. Group:Switch:OR(ON,OFF)
    if item.get("itemType") == "Group":
        base = item.get("baseItemType")
        fn = item.get("functionName")
        if base:
            spec = f"Group:{base}"
            if fn:
                args = ",".join(item.get("functionParams") or [])
                spec += f":{fn}({args})" if args else f":{fn}"
            parts = [spec]

    parts.append(name)

    if item.get("label"):
        parts.append(f'"{esc(item["label"])}"')
    if item.get("category"):
        parts.append(f'<{item["category"]}>')
    if item.get("groupNames"):
        parts.append("(" + ", ".join(item["groupNames"]) + ")")
    if item.get("tags"):
        parts.append("[" + ", ".join(f'"{t}"' for t in item["tags"]) + "]")
    if channels:
        if len(channels) == 1:
            parts.append(f'{{ channel="{channels[0]}" }}')
        else:
            joined = '",\n              channel="'.join(channels)
            parts.append(f'{{ channel="{joined}" }}')

    return " ".join(parts)


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2

    items = {
        entry["value"]["name"] if "name" in entry.get("value", {}) else key: entry["value"]
        for key, entry in json.loads(Path(sys.argv[1]).read_text()).items()
        if entry.get("value")
    }

    links: dict[str, list[str]] = defaultdict(list)
    for entry in json.loads(Path(sys.argv[2]).read_text()).values():
        link = entry.get("value")
        if not link:
            continue
        links[link["itemName"]].append(link["channelUID"]["uid"])

    lines = [
        "// Items converted from JSONDB by scripts/jsondb-to-items.py",
        "// Channel links are inline here, which is what makes",
        "// org.openhab.core.thing.link.ItemChannelLink.json unnecessary.",
        "",
    ]

    linked = unlinked = 0
    for name in sorted(items):
        channels = sorted(links.get(name, []))
        if channels:
            linked += 1
        else:
            unlinked += 1
        lines.append(render_item(name, items[name], channels))

    Path(sys.argv[3]).write_text("\n".join(lines) + "\n")

    orphans = set(links) - set(items)
    print(f"  {len(items)} items -> {sys.argv[3]}")
    print(f"    {linked} with channel links, {unlinked} without")
    if orphans:
        print(f"    WARNING: {len(orphans)} links reference unknown items: {sorted(orphans)[:5]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
