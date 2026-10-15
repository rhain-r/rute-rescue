[out:json][timeout:180];
(
  way["highway"]["bridge"]["bridge"!="no"](14.35,120.90,14.79,121.14);
  way["highway"]["tunnel"]["tunnel"!~"^(no|building_passage|culvert)$"](14.35,120.90,14.79,121.14);
);
out geom tags;
