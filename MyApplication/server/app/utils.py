from geopy.distance import geodesic
import geohash2 as geohash
import numpy as np

def get_geohashes_within_radius(lat, lon, radius_km, precision=7):
    # Calculate the bounding box for the geohashes
    lat_delta = radius_km / 111  # Approximately 111 km per degree of latitude
    lon_delta = radius_km / (111 * np.cos(np.radians(lat)))  # Correction for longitude

    min_lat = lat - lat_delta
    max_lat = lat + lat_delta
    min_lon = lon - lon_delta
    max_lon = lon + lon_delta

    # Determines the step in degrees for the iteration
    cell_height = 0.153  # Approximate height of the cell at precision 7 in km
    cell_width = 0.153  # Approximate width of precision cell 7 in km
    lat_step = cell_height / 111
    lon_step = cell_width / (111 * np.cos(np.radians(lat)))

    geohashes = set()
    lat_iter = min_lat
    while lat_iter <= max_lat:
        lon_iter = min_lon
        while lon_iter <= max_lon:
            gh = geohash.encode(lat_iter, lon_iter, precision=precision)
            # Calculates the distance from the center of the geohash to the point
            gh_lat, gh_lon = geohash.decode(gh)
            distance = geodesic((lat, lon), (gh_lat, gh_lon)).km
            if distance <= radius_km:
                geohashes.add(gh)
            lon_iter += lon_step
        lat_iter += lat_step

    return list(geohashes)
