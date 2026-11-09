# MapLibre and AndroidX ship their own consumer rules.

# BRouter's car profile names its path model by class, which BRouter then creates by name.
-keep class * extends btools.router.OsmPathModel { <init>(); }
