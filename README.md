# Material Maps

A small, fast maps app for Android, built with Jetpack Compose and Material You. It runs on open data only: no Google Play Services, no accounts, no API keys, no tracking.

Search places, get driving, cycling, walking and public transport directions, follow turn-by-turn navigation, and check live departures at any stop or station. Public transport works best in the Netherlands, with live data for NS, GVB, RET, HTM, Arriva, Qbuzz and others.

## Features

### Map
- OpenStreetMap vector map from [OpenFreeMap](https://openfreemap.org), rendered with [MapLibre](https://maplibre.org): 3D buildings, shops, cafés, sights and transit stops.
- Light and dark themes follow the system. The dark map is generated from the detailed light style and recoloured with your Material You (wallpaper) colours, so it matches the rest of the app.
- Layers: satellite photos (with earlier years' aerial photos of the Netherlands), terrain shading and contour lines; rain radar, clouds, live satellite and lightning of the last hours, and wind, cloud, storm and air quality forecasts (including CO₂ and methane) for the coming days, each with a time slider; measured air quality from stations and sensors; train, metro, tram and bus lines and the rail network; hiking, cycling, mountain biking and ski routes and sea marks; charging stations, drinking water, toilets, AEDs, bike parking, bike repair stations and Wikipedia articles as pins; and, for the Netherlands, elevation (AHN) and land parcels.
- Compass button that only appears when the map is rotated or tilted; tap it to point north again.
- Opens where you last left the map, then jumps to your location.

### Your location
- Blue dot with a smooth live heading arrow.
- The location button steps through modes: **center** on you → **zoom** to street level → **rotate** the map with the phone → back to north-up.

### Search
- Results appear as you type, biased toward what's on screen.
- General terms find everything nearby: `albert` lists every Albert Heijn around you, sorted by distance, plus other close matches. Specific names like `utrecht` still find the city.
- **Show all on map** (or the enter key) puts every result on the map as a pin.
- Stations and stops appear in results too, and you can enter coordinates like `52.37, 4.89`.
- Recent places are shown when the search box is empty.
- Tap anything on the map (a shop, a sight, a bus stop) to open it, or long-press to drop a pin.

### Directions
- **Drive, bike and walk:** up to three routes with time, distance and arrival time, plus a step-by-step list. Tap a grey route on the map to switch to it.
- **Turn-by-turn navigation:** a large next-turn banner, distance and time remaining, automatic rerouting when you go off route, and the screen stays on. Instructions follow the phone's language.
- **Public transport:** journeys with line badges in the operators' own colours, platforms, live delays and cancellations, and a full timeline for each journey (where to board, how many stops, where to get off, walking parts). You can choose *leave now*, *depart at* or *arrive by*, and load later connections. On the map, rides are drawn in the line colours and walks as dots.
- **Open in another app** passes the destination to Google Maps, Organic Maps, OsmAnd and the like.

### Offline maps
- Download a continent's country, a country or a province from Settings › Offline maps: browse the world or search by name. Each region comes with its map and its routing data.
- Without a connection, the map still works there, and so do driving, cycling and walking directions with turn-by-turn navigation, worked out on the phone with [BRouter](https://github.com/abrensch/brouter). Offline instructions are in English.
- Downloads carry on in the background, and pick up where they stopped.

### Departures
- Every stop and station shows live departures: line, destination, platform, "in 4 min", delays (with the planned time struck through) and cancellations.
- A tab for each kind of transport that stops there (train, metro, tram, bus, ferry).
- Refreshes every 30 seconds while the card is open.

## Install

GitHub Actions builds a signed APK on every push to `main` and publishes it under [**Releases**](../../releases) as `material-maps.apk`. Open the latest release on your phone and install it. You need to allow "install unknown apps" for your browser once.

Requires Android 8.0 or newer. On Android 12+ the app picks up your wallpaper colours.

## Building

You need JDK 17+ and the Android SDK. Android Studio sets both up.

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew installRelease           # build and install on a connected device
```

Release builds are unsigned unless you provide a key, either in an untracked `keystore.properties` in the project root:

```properties
storeFile=keystore/materialmaps.jks
storePassword=...
keyAlias=...
keyPassword=...
```

or through the environment variables `MM_KEYSTORE_FILE`, `MM_KEYSTORE_PASSWORD`, `MM_KEY_ALIAS` and `MM_KEY_PASSWORD`. CI reads them from the repository secrets `KEYSTORE_BASE64` (the base64-encoded keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## Releasing

F-Droid builds from git tags. To release, bump `versionCode` and `versionName` in `app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, commit, and tag the commit `v<versionName>` (for example `v0.1.0`). The store listing lives in `fastlane/metadata/android/`.

## How it's built

Kotlin, Jetpack Compose, Material 3 and the MapLibre Android SDK. There's no other networking or JSON library: plain `HttpURLConnection` and `org.json`. [AboutLibraries](https://github.com/mikepenz/AboutLibraries) generates the license list at build time.

| File | What it does |
| --- | --- |
| `MapScreen.kt` | The main screen: map setup and layers, search, place cards, directions and navigation state |
| `DarkStyle.kt` | Turns the light map style into a Material You dark style |
| `HeadingEngine.kt` | Fast, smoothed compass heading for the location dot |
| `Photon.kt` | Place search (`Geocoder`), the Photon fallback and recent places |
| `Routing.kt` | Valhalla routing, route progress for navigation, distance and time formatting |
| `Transit.kt` | Transitous journey planning, departures and geocoding |
| `SearchUi.kt`, `DirectionsUi.kt`, `TransitUi.kt` | Compose UI for results, routes, navigation, journeys and departure boards |
| `Credits.kt` | The credits sheet and open-source licenses screen |
| `Http.kt` | A small HTTP GET helper |
| `Layers.kt`, `AerialTiles.kt` | Satellite photos, terrain and the other map overlays |
| `ContourTiles.kt` | Contour lines, traced on the phone from elevation tiles and served to MapLibre as vector tiles |
| `AreaOverlays.kt` | Overlays looked up for the area in view: place pins, bus routes, and the rain radar |
| `Regions.kt`, `OfflineMapsUi.kt`, `OfflineMaps.kt` | Regions to download (by Geofabrik), their downloads, and the offline maps page |
| `OfflineRouting.kt` | Directions on the phone with BRouter, and falling back to it without a connection |
| `brouter/` | BRouter's routing engine (v1.7.10, MIT), kept as it is since it isn't published as a library |
| `Weather.kt` | Satellite pictures, CAMS air quality, weather forecasts drawn on the phone from a grid of points, and measured air quality |
| `Loading.kt` | Tracks what the overlays are loading, for the indicator on the map |

## Data and services

All of these are free to use, and most are community-run. Please be kind to them.

| What | Service |
| --- | --- |
| Map tiles and styles | [OpenFreeMap](https://openfreemap.org) |
| Driving, cycling and walking routes | [Valhalla](https://github.com/valhalla/valhalla) on the [FOSSGIS](https://www.fossgis.de) server |
| Public transport, departures, search and bus routes | [Transitous](https://transitous.org) ([MOTIS](https://github.com/motis-project/motis)), with Dutch data from [OpenOV](https://openov.nl) |
| Search fallback | [Photon](https://photon.komoot.io) by komoot |
| Offline directions | [BRouter](https://brouter.de) routing engine and routing data; regions by [Geofabrik](https://download.geofabrik.de) |
| Terrain shading and contour lines | [Terrain Tiles](https://registry.opendata.aws/terrain-tiles/) on AWS ([sources](https://github.com/tilezen/joerd/blob/master/docs/attribution.md)) |
| Trail, rail, ski and sea overlays | [Waymarked Trails](https://waymarkedtrails.org) (CC BY-SA 3.0), [OpenRailwayMap](https://www.openrailwaymap.org) (CC BY-SA 2.0), [OpenSnowMap](https://www.opensnowmap.org) and [OpenSeaMap](https://www.openseamap.org) (CC BY-SA 2.0), and [VersaTiles](https://versatiles.org) for city-wide metro and tram lines |
| Rain radar | [RainViewer](https://www.rainviewer.com) |
| Cloud, satellite and lightning pictures | © [EUMETSAT](https://view.eumetsat.int) (Meteosat Third Generation) |
| Wind, cloud and storm forecasts | [Open-Meteo](https://open-meteo.com) (CC BY 4.0) |
| Air quality, UV, CO₂ and methane forecasts | [Copernicus Atmosphere Monitoring Service](https://atmosphere.copernicus.eu), drawn by [ECMWF](https://charts.ecmwf.int) (CC BY 4.0) |
| Measured air quality | [Luchtmeetnet](https://www.luchtmeetnet.nl) (Dutch stations) and [Sensor.Community](https://sensor.community) (ODbL) |
| Elevation and parcels of the Netherlands | [AHN](https://www.ahn.nl) and the [Kadaster](https://www.kadaster.nl) via [PDOK](https://www.pdok.nl) (CC0) |
| Place details and AED and bike repair pins | [Nominatim](https://nominatim.org) and the [Overpass API](https://overpass-api.de) |
| Place descriptions and articles on the map | [Wikipedia](https://www.wikipedia.org) and [Wikidata](https://www.wikidata.org) (CC BY-SA) |
| Satellite photos | [Sentinel-2 cloudless 2016](https://s2maps.eu) by EOX (CC BY 4.0), with aerial photos of the Netherlands from 2016 on from [Beeldmateriaal Nederland](https://www.beeldmateriaal.nl) via [PDOK](https://www.pdok.nl) (CC BY 4.0) |

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the ODbL.

## Privacy

The app has no analytics, accounts or ads. Your location stays on the device, except when it has to be sent to work a feature: the map area or your position goes to the search service for nearby results, the map area goes to Overpass, Wikipedia, Transitous and Open-Meteo while AED or bike repair pins, Wikipedia pins, bus routes or weather forecasts are on, and your start point goes to the routing service for directions. Recent places and the last map position are stored only on the phone.

The ⓘ button on the map opens the in-app credits: all of the above, plus every bundled library and its license.

## License

Material Maps is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License v3.0](LICENSE).

The map data, tiles and services listed above have their own licenses and usage policies. In particular, [Transitous](https://transitous.org/api/) may only be used by open-source, non-commercial apps, and the FOSSGIS servers are for light use only. If you fork this app for a large audience, run your own servers or talk to these projects first.
