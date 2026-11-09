#!/usr/bin/env python3
"""
Builds app/src/main/assets/place_kinds.json: the kinds of places people search for ("supermarket",
"cafe", "dentist"), with their names and search terms in a few languages, from the iD editor's
tagging schema (ISC license). The app matches what's typed against these to find, say, every
supermarket nearby instead of places called "supermarket".

    python3 scripts/place_kinds.py
"""
import json
import pathlib
import urllib.request

SCHEMA = "https://cdn.jsdelivr.net/npm/@openstreetmap/id-tagging-schema/dist"
LANGUAGES = ["en", "nl", "de", "fr", "es", "it"]
KEYS = {"amenity", "shop", "tourism", "leisure", "craft", "office", "healthcare", "emergency",
        "historic", "sport", "public_transport", "railway", "aeroway", "natural", "man_made"}
# Presets that aren't a single tag in the schema, as the tag the map and Overpass use for them.
REMAP = {
    "public_transport/station_train": ("railway", "station"),
}
# Plainer names where the schema's are for mappers, and words people search with that it lacks.
LABELS = {
    "amenity/pharmacy": {"en": "Pharmacy"},
    "amenity/hospital": {"en": "Hospital"},
    "amenity/toilets": {"en": "Toilets"},
    "public_transport/station_train": {"en": "Train station"},
}
EXTRA_TERMS = {
    "amenity/pharmacy": {"en": ["pharmacy", "chemist"], "nl": ["apotheek"]},
    "amenity/toilets": {"en": ["wc", "toilet", "bathroom", "loo"], "nl": ["wc", "toilet"]},
    "shop/bicycle": {"en": ["bike shop", "bike repair"], "nl": ["fietsenmaker", "fietswinkel", "fietsreparatie"]},
    "public_transport/station_train": {"en": ["station", "train station", "railway station"], "nl": ["station", "treinstation"]},
    "amenity/fuel": {"en": ["petrol", "petrol station"], "nl": ["benzinepomp", "pomp"]},
}
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/place_kinds.json"


def get(path):
    with urllib.request.urlopen(f"{SCHEMA}/{path}") as response:
        return json.load(response)


def main():
    presets = get("presets.min.json")
    translations = {lang: get(f"translations/{lang}.min.json")[lang]["presets"]["presets"] for lang in LANGUAGES}
    kinds = []
    for preset_id, preset in presets.items():
        tags = preset.get("tags", {})
        if preset_id in REMAP:
            key, value = REMAP[preset_id]
        else:
            if len(tags) != 1 or preset.get("searchable") is False:
                continue
            (key, value), = tags.items()
            if key not in KEYS or value in ("*", "yes") or preset_id != f"{key}/{value}":
                continue
        if not {"point", "area", "vertex"} & set(preset.get("geometry", [])):
            continue
        names, terms = {}, {}
        for lang, strings in translations.items():
            text = strings.get(preset_id)
            if not text or not text.get("name"):
                continue
            names[lang] = LABELS.get(preset_id, {}).get(lang, text["name"])
            words = text.get("terms", [])
            if isinstance(words, str):
                words = words.split(",")
            words += text.get("aliases", []) if isinstance(text.get("aliases"), list) else str(text.get("aliases", "")).split("\n")
            words += EXTRA_TERMS.get(preset_id, {}).get(lang, [])
            words = sorted({w.strip() for w in words if w.strip() and w.strip().lower() != names[lang].lower()})
            if words:
                terms[lang] = words
        if names:
            kinds.append({"k": key, "v": value, "n": names, "t": terms})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(kinds, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(kinds)} kinds, {OUT.stat().st_size // 1024} KB -> {OUT}")


if __name__ == "__main__":
    main()
