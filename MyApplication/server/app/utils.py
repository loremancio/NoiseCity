from geopy.distance import geodesic
import geohash2 as geohash
import numpy as np

def get_geohashes_within_radius(lat, lon, radius_km, precision=7):
    # Calcola il bounding box
    lat_delta = radius_km / 111  # Circa 111 km per grado di latitudine
    lon_delta = radius_km / (111 * np.cos(np.radians(lat)))  # Correzione per la longitudine

    min_lat = lat - lat_delta
    max_lat = lat + lat_delta
    min_lon = lon - lon_delta
    max_lon = lon + lon_delta

    # Determina il passo in gradi per l'iterazione
    cell_height = 0.153  # Altezza approssimativa della cella a precisione 7 in km
    cell_width = 0.153  # Larghezza approssimativa della cella a precisione 7 in km
    lat_step = cell_height / 111
    lon_step = cell_width / (111 * np.cos(np.radians(lat)))

    geohashes = set()
    lat_iter = min_lat
    while lat_iter <= max_lat:
        lon_iter = min_lon
        while lon_iter <= max_lon:
            gh = geohash.encode(lat_iter, lon_iter, precision=precision)
            # Calcola la distanza tra il centro della cella e il punto centrale
            gh_lat, gh_lon = geohash.decode(gh)
            distance = geodesic((lat, lon), (gh_lat, gh_lon)).km
            if distance <= radius_km:
                geohashes.add(gh)
            lon_iter += lon_step
        lat_iter += lat_step

    return list(geohashes)
