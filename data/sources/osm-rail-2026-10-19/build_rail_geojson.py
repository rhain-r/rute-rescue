"""
Builds app/src/main/assets/transit/metro_manila_rail.geojson from OpenStreetMap data.

Input (this folder, downloaded from the Overpass API on 2026-10-19):
  lines.json       relations 10463194, 110410, 109159 with geometry (out geom)
  lrt1_lines.json  relations 110418, 8000260 with geometry (the full LRT-1, FPJ to Dr. Santos)
  stops.json, lrt1_stops.json   the stop nodes of those relations (with names)

Output: one GeoJSON FeatureCollection with
  - a MultiLineString per line (kind=line): line, name, colour
  - a Point per station (kind=station): name, line, colour

Only lines running on 2026-10-19 are kept (LRT-1, LRT-2, MRT-3). The PNR Metro Commuter
(suspended since 2024 for the North-South Commuter Railway works) and the Metro Manila
Subway (under construction) are left out on purpose.

Usage:  python build_rail_geojson.py <output.geojson>
Data (c) OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
"""
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# relation id -> (short line name, display name)
LINES = {
    110418: ("LRT-1", "LRT Line 1"),
    110410: ("LRT-2", "LRT Line 2"),
    109159: ("MRT-3", "MRT Line 3"),
}
# OpenStreetMap stop names that need fixing (checked by position and station code)
NAME_FIXES = {
    "/": "United Nations",  # LRT-1 GL13 at UN Avenue / Taft Avenue
}


def load(name):
    with io.open(os.path.join(HERE, name), encoding="utf-8") as f:
        return json.load(f)["elements"]


def rounded(lon, lat):
    return [round(lon, 5), round(lat, 5)]


def main(out_path):
    relations = {r["id"]: r for r in load("lines.json") + load("lrt1_lines.json")}
    nodes = {n["id"]: n for n in load("stops.json") + load("lrt1_stops.json")}
    features = []
    for relation_id, (line, display) in LINES.items():
        relation = relations[relation_id]
        colour = relation["tags"].get("colour", "#555555")
        parts = []
        for member in relation["members"]:
            if member["type"] == "way" and member["role"] in ("", "track", "forward", "backward"):
                coords = [rounded(p["lon"], p["lat"]) for p in member.get("geometry", [])]
                if len(coords) >= 2:
                    parts.append(coords)
        features.append({
            "type": "Feature",
            "properties": {"kind": "line", "line": line, "name": display, "colour": colour},
            "geometry": {"type": "MultiLineString", "coordinates": parts},
        })
        seen = set()
        for member in relation["members"]:
            if member["type"] != "node" or not member["role"].startswith("stop"):
                continue
            node = nodes.get(member["ref"])
            if node is None:
                continue
            name = node.get("tags", {}).get("name", "").strip()
            name = NAME_FIXES.get(name, name)
            if not name or name in seen:
                continue
            seen.add(name)
            features.append({
                "type": "Feature",
                "properties": {"kind": "station", "name": name, "line": line, "colour": colour},
                "geometry": {"type": "Point", "coordinates": rounded(node["lon"], node["lat"])},
            })
    collection = {
        "type": "FeatureCollection",
        "attribution": "Rail lines and stations (c) OpenStreetMap contributors, ODbL 1.0",
        "retrieved": "2026-10-19",
        "note": "Running lines only: LRT-1, LRT-2, MRT-3. PNR (suspended) and the Metro Manila Subway "
                "(under construction) are not included.",
        "features": features,
    }
    with io.open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(collection, f, ensure_ascii=False, separators=(",", ":"))
    lines = [x for x in features if x["properties"]["kind"] == "line"]
    stations = [x for x in features if x["properties"]["kind"] == "station"]
    print("wrote", out_path, "-", len(lines), "lines,", len(stations), "stations")
    for x in lines:
        line = x["properties"]["line"]
        count = sum(1 for s in stations if s["properties"]["line"] == line)
        print(" ", line, x["properties"]["colour"], count, "stations")


if __name__ == "__main__":
    main(sys.argv[1])
