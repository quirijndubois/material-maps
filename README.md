# Material Maps

A small, fast maps app for Android, built with Jetpack Compose and Material You. It runs on open data only: no Google Play Services, no accounts, no API keys, no tracking.

Search places, get driving, cycling, walking and public transport directions, follow turn-by-turn navigation, and check live departures at any stop or station. Public transport works best in the Netherlands, with live data for NS, GVB, RET, HTM, Arriva, Qbuzz and others.

## Features

### Map
- OpenStreetMap vector map from [OpenFreeMap](https://openfreemap.org), rendered with [MapLibre](https://maplibre.org): 3D buildings, shops, cafés, sights and transit stops.
- Light and dark themes follow the system. The dark map is generated from the detailed light style and recoloured with your Material You (wallpaper) colours, so it matches the rest of the app.
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

The signing key in `keystore/` is committed on purpose, so every build (local or CI) installs over the previous one. That's fine for a personal, sideloaded app. Use your own private key before publishing to a store.

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

## Data and services

All of these are free, community-run services. Please be kind to them.

| What | Service |
| --- | --- |
| Map tiles and styles | [OpenFreeMap](https://openfreemap.org) |
| Driving, cycling and walking routes | [Valhalla](https://github.com/valhalla/valhalla) on the [FOSSGIS](https://www.fossgis.de) server |
| Public transport, departures and search | [Transitous](https://transitous.org) ([MOTIS](https://github.com/motis-project/motis)), with Dutch data from [OpenOV](https://openov.nl) |
| Search fallback | [Photon](https://photon.komoot.io) by komoot |

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the ODbL.

## Privacy

The app has no analytics, accounts or ads. Your location stays on the device, except when it has to be sent to work a feature: the map area or your position goes to the search service for nearby results, and your start point goes to the routing service for directions. Recent places and the last map position are stored only on the phone.

The ⓘ button on the map opens the in-app credits: all of the above, plus every bundled library and its license.

## License

Material Maps is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License v3.0](LICENSE).

The map data, tiles and services listed above have their own licenses and usage policies. In particular, [Transitous](https://transitous.org/api/) may only be used by open-source, non-commercial apps, and the FOSSGIS servers are for light use only. If you fork this app for a large audience, run your own servers or talk to these projects first.
