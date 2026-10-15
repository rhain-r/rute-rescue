/*
  RutaRescue road-structure extract (bridges, flyovers, tunnels, underpasses) for Metro Manila.
  Run at https://overpass-api.de/api/interpreter (POST the query as "data"), save the JSON as
  data/raw/osm/structures_overpass.json, then run:  ./gradlew :tools:dataprep:prepareStructures
  Data (c) OpenStreetMap contributors, ODbL 1.0.
*/
[out:json][timeout:180];
(
  way["highway"]["bridge"]["bridge"!="no"](14.35,120.90,14.79,121.14);
  way["highway"]["tunnel"]["tunnel"!~"^(no|building_passage|culvert)$"](14.35,120.90,14.79,121.14);
);
out geom tags;
