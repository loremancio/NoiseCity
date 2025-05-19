from app.extensions import mongo
from app.models import User
from bson import ObjectId
from app.extensions import bcrypt
import geohash2 as Geohash
import reverse_geocode
from datetime import datetime # Import datetime for explicit type handling

# from pymongo import ReturnDocument # This import is commented out in the original code, so we keep it that way.

# Achievement thresholds
ACH_THRESHOLD_MEASUREMENTS = 5
ACH_THRESHOLD_CITIES = 4
ACH_THRESHOLD_COUNTRIES = 2

class UserRepository:
    @staticmethod
    def get_by_username(username):
        user_data = mongo.db.users.find_one({'username': username})
        return User.from_mongo(user_data)

    @staticmethod
    def get_by_id(user_id):
        try:
            user_data = mongo.db.users.find_one({'_id': ObjectId(user_id)})
            return User.from_mongo(user_data)
        except Exception:
            return None

    @staticmethod
    def create_user(username, password_hash):
        if mongo.db.users.find_one({'username': username}):
            return None
        user_id = mongo.db.users.insert_one({'username': username, 'password': password_hash}).inserted_id
        return str(user_id)

    @staticmethod
    def register(username, password):
        if UserRepository.get_by_username(username):
            return None, 'Username already exists'
        pw_hash = bcrypt.generate_password_hash(password).decode('utf-8')
        user_id = UserRepository.create_user(username, pw_hash)
        if user_id:
            user = UserRepository.get_by_id(user_id)
            return user, None
        return None, 'User creation error'

    @staticmethod
    def authenticate(username, password):
        user = UserRepository.get_by_username(username)
        if user and bcrypt.check_password_hash(user.password_hash, password):
            return user
        return None

    # Increment the measurement count for a user
    @staticmethod
    def increment_measurement_count(username):
        """
        Increments the measurement count for a given user.

        :param username: str, the username of the user.
        :return: int or None, the updated count or None if an error occurred.
        """
        try:
            # Use return_document=ReturnDocument.AFTER to get the updated document
            # However, ReturnDocument is commented out, so we will just increment
            # and potentially retrieve the count separately if needed, or rely
            # on the fact that find_one_and_update returns the document before update by default
            # unless return_document is specified.
            updated_user_doc = mongo.db.users.find_one_and_update(
                {'username': username},
                {'$inc': {'count': 1}},
                # Uncomment the line below if you import ReturnDocument and want the document AFTER update
                # return_document=ReturnDocument.AFTER
            )
            # If return_document=ReturnDocument.AFTER was used, you could return updated_user_doc['count']
            # Since it's not used, we can't reliably get the *new* count from the return value directly
            # unless we perform another query or change the logic to use return_document.
            # For now, we'll just confirm the update happened. A subsequent call to get_user_measurement_count
            # is needed to get the precise *new* count.
            return updated_user_doc is not None # Return True if update was successful, False otherwise
        except Exception as e:
            print(f"Error incrementing count for user {username}: {e}")
            return False # Indicate failure


    # Get the measurement count for a user
    @staticmethod
    def get_user_measurement_count(username):
        """
        Gets the measurement count for a given user.

        :param username: str, the username of the user.
        :return: int or None, the count variable or None if user not found.
        """
        user = UserRepository.get_by_username(username)
        if user and hasattr(user, 'count'):
             return user.count # Assuming User model has a 'count' attribute
        return 0 # Return 0 if user not found or count not present (initial state)


    # Add an achievement to a user
    @staticmethod
    def add_achievement(username, achievement):
        """
        Adds an achievement to a user's list of achievements.

        :param username: str, the username of the user.
        :param achievement: dict, the achievement document to add.
        :return: bool, True if the achievement was added (or already exists), False otherwise.
        """
        try:
            # Use $addToSet to add the achievement only if it's not already in the list
            result = mongo.db.users.update_one(
                {'username': username},
                {'$addToSet': {'achievements': achievement}}
            )
            # Check if a modification occurred (either added or already present)
            return result.modified_count > 0 or result.matched_count > 0
        except Exception as e:
            print(f"Error adding achievement for user {username}: {e}")
            return False

class MeasurementRepository:

    @staticmethod
    def process_measurement(user_id, timestamp, noise_level, location, duration):
        """
        Processes a new noise measurement, inserting raw data, aggregating it,
        and checking for user achievements based on measurement count, visited cities,
        and visited countries.

        :param user_id: str, the username of the user making the measurement.
        :param timestamp: datetime, the timestamp of the measurement.
        :param noise_level: float, the measured noise level.
        :param location: dict, containing 'type' and 'coordinates' [lon, lat].
        :param duration: int, the duration of the measurement in seconds.
        :return: dict, a dictionary of achievements earned, or True if no achievements earned but processing was successful.
                 Raises an exception if a critical error occurs during database operations.
        """
        # --- Step 1: Validate input (assuming validazione invariate means this part is handled elsewhere or is okay)
        # Convert timestamp to timezone-naive if it's timezone-aware for consistency with DB operations if needed.
        # The original code removes tzinfo for now, so we'll stick to that.
        now_naive = timestamp.replace(tzinfo=None)

        # Generate geohash for the location
        # geohash2 encode expects latitude, longitude
        geohash = Geohash.encode(location['coordinates'][1], location['coordinates'][0], precision=7)

        # Prepare the raw measurement document
        raw_doc = {
            'user_id': user_id, # Using username as user_id as per usage in UserRepository
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': geohash,
            'duration': duration,
        }
        raw_measurement_id = None # To store the inserted raw document ID for rollback

        # Prepare the aggregated measurement time bucket
        time_bucket = timestamp.replace(minute=0, second=0, microsecond=0)

        # Dictionary to collect earned achievements
        earned_achievements = {'achievements': []}

        try:
            # --- Step 2: Insert the raw measurement
            insert_result = mongo.db.raw_measurements.insert_one(raw_doc)
            raw_measurement_id = insert_result.inserted_id

            # --- Step 3: Upsert the aggregated measurement for the time bucket and geohash
            # Use an atomic upsert to increment sum and count
            mongo.db.aggregated_measurements.update_one(
                { 'geohash': geohash, 'time_bucket': time_bucket },
                {
                    '$inc': {
                        'sum_noise': noise_level,
                        'count': 1
                    },
                    '$setOnInsert': {
                        # Save the cell center on the first insertion
                        'center': {
                            'type': 'Point',
                            'coordinates': [
                                location['coordinates'][0], # longitude
                                location['coordinates'][1]  # latitude
                            ]
                        }
                    }
                },
                upsert=True
            )

        except Exception as e:
            # --- Rollback Step: If aggregation fails, attempt to remove the raw measurement
            print(f"Error during aggregated measurement upsert: {e}")
            if raw_measurement_id:
                 # Check if the raw measurement was actually inserted before trying to delete
                try:
                    delete_result = mongo.db.raw_measurements.delete_one({'_id': raw_measurement_id})
                    if delete_result.deleted_count > 0:
                        print(f"Successfully rolled back raw measurement insertion with id: {raw_measurement_id}")
                    else:
                         print(f"Raw measurement with id {raw_measurement_id} not found for rollback.")
                except Exception as rollback_e:
                    print(f"Error during raw measurement rollback for id {raw_measurement_id}: {rollback_e}")
            # Re-raise the original exception after attempting rollback
            raise

        # --- Step 4: Check for achievements

        # Get location information using reverse geocoding
        # reverse_geocode expects a list of (latitude, longitude) tuples
        geo_info = reverse_geocode.get(location['coordinates'][::-1])

        # 4.1) Check for Measurement Count Achievement
        # Increment the user's total measurement count
        increment_success = UserRepository.increment_measurement_count(user_id)

        if increment_success:
            current_measurement_count = UserRepository.get_user_measurement_count(user_id)
            if current_measurement_count == ACH_THRESHOLD_MEASUREMENTS:
                achievement_data = {
                    'title': 'Measurement Master',
                    'description': f'You have made {ACH_THRESHOLD_MEASUREMENTS} measurements'
                }
                if UserRepository.add_achievement(user_id, achievement_data):
                     earned_achievements['achievements'].append(achievement_data)
                     print(f"User {user_id} earned 'Measurement Master' achievement.")
                else:
                     print(f"Failed to add 'Measurement Master' achievement for user {user_id}.")
        else:
            print(f"Failed to increment measurement count for user {user_id}.")


        # 4.2) Check for Cities Visited Achievement
        city_name = geo_info.get('city')
        country_name = geo_info.get('country') # Also get country name for potential use

        if city_name:
            # Get the count of distinct cities visited by the user BEFORE this measurement
            old_city_count = mongo.db.user_cities.count_documents({ "user_id": user_id })

            try:
                # Upsert the user's visit information for this city
                mongo.db.user_cities.update_one(
                    { "user_id": user_id, "city": city_name },
                    {
                        "$inc": { "visit_count": 1 },
                        "$setOnInsert": {
                            "country": country_name,
                            "first_visit": now_naive # Use naive datetime
                        },
                        "$set": { "last_visit": now_naive } # Use naive datetime
                    },
                    upsert=True
                )

                # Get the count of distinct cities visited by the user AFTER this measurement
                new_city_count = mongo.db.user_cities.count_documents({ "user_id": user_id })

                # Check if the new count crossed the threshold AND it was a new city visit that caused the count to increase
                if new_city_count == ACH_THRESHOLD_CITIES and new_city_count > old_city_count:
                    achievement_data = {
                        'title': 'City Explorer',
                        'description': f'You have visited {ACH_THRESHOLD_CITIES} cities'
                    }
                    if UserRepository.add_achievement(user_id, achievement_data):
                        earned_achievements['achievements'].append(achievement_data)
                        print(f"User {user_id} earned 'City Explorer' achievement.")
                    else:
                        print(f"Failed to add 'City Explorer' achievement for user {user_id}.")

            except Exception as e:
                print(f"Error processing city visit achievement for user {user_id}: {e}")
        else:
            print(f"Could not retrieve city name for location: {location}")


        # 4.3) Check for Countries Visited Achievement
        if country_name:
            # Get the count of distinct countries visited by the user BEFORE this measurement
            old_country_count = mongo.db.user_countries.count_documents({ "user_id": user_id })

            try:
                # Upsert the user's visit information for this country
                mongo.db.user_countries.update_one(
                    { "user_id": user_id, "country": country_name },
                    {
                        "$inc": { "visit_count": 1 },
                        "$setOnInsert": {
                            "first_visit": now_naive # Use naive datetime
                        },
                        "$set": { "last_visit": now_naive } # Use naive datetime
                    },
                    upsert=True
                )

                # Get the count of distinct countries visited by the user AFTER this measurement
                new_country_count = mongo.db.user_countries.count_documents({ "user_id": user_id })

                # Check if the new count crossed the threshold AND it was a new country visit that caused the count to increase
                if new_country_count == ACH_THRESHOLD_COUNTRIES and new_country_count > old_country_count:
                     achievement_data = {
                        'title': 'World Traveler',
                        'description': f'You have visited {ACH_THRESHOLD_COUNTRIES} countries'
                    }
                     if UserRepository.add_achievement(user_id, achievement_data):
                        earned_achievements['achievements'].append(achievement_data)
                        print(f"User {user_id} earned 'World Traveler' achievement.")
                     else:
                         print(f"Failed to add 'World Traveler' achievement for user {user_id}.")

            except Exception as e:
                 print(f"Error processing country visit achievement for user {user_id}: {e}")
        else:
            print(f"Could not retrieve country name for location: {location}")


        # Return the dictionary of earned achievements or True if no achievements were earned
        return earned_achievements if earned_achievements else True


    @staticmethod
    def get_aggregated_by_geohash(lat, lon, radius_km, start_ts=None, end_ts=None):
        """
        Retrieve aggregated measurements within a given radius around a point,
        and optional time range, computing the average intensity on the fly.

        :param lat:       float, latitude of the center point
        :param lon:       float, longitude of the center point
        :param radius_km: float, search radius in kilometers
        :param start_ts:  datetime, inclusive start of time range (optional)
        :param end_ts:    datetime, inclusive end of time range (optional)
        :return: List of dicts with keys 'lat', 'lon', 'intensity', 'count', 'distance_m'
        """
        # Convert radius from kilometers to meters
        radius_m = radius_km * 1000

        # MongoDB aggregation pipeline
        pipeline = []

        # 1) GeoNear Stage: Filter spatially and calculate distance
        # Requires a geospatial index on the 'center' field in aggregated_measurements collection
        geo_near_stage = {
            '$geoNear': {
                'near': { 'type': 'Point', 'coordinates': [lon, lat] }, # MongoDB uses [longitude, latitude]
                'distanceField': 'dist_m', # Output field for distance from the center point
                'maxDistance': radius_m,   # Maximum distance in meters
                'spherical': True          # Calculate distances using spherical geometry
            }
        }

        # Add optional time filter to the $geoNear query
        if start_ts or end_ts:
            time_bucket_query = {}
            if start_ts:
                # Start of the hour for the start timestamp
                time_bucket_query['$gte'] = start_ts.replace(minute=0, second=0, microsecond=0, tzinfo=None) # Ensure naive datetime
            if end_ts:
                # Start of the hour for the end timestamp
                time_bucket_query['$lte'] = end_ts.replace(minute=0, second=0, microsecond=0, tzinfo=None) # Ensure naive datetime
            geo_near_stage['$geoNear']['query'] = { 'time_bucket': time_bucket_query }

        pipeline.append(geo_near_stage)

        # 2) Group Stage: Group by geohash to aggregate measurements within the same geohash cell
        group_stage = {
            '$group': {
                '_id': '$geohash',           # Group by the geohash value
                'sum_noise': { '$sum': '$sum_noise' }, # Sum of noise levels
                'count':     { '$sum': '$count' },     # Sum of measurement counts
                'center':    { '$first': '$center' },  # Get the center point (should be the same for a given geohash)
                 # Capture the distance from the $geoNear stage. Since we group by geohash,
                 # there might be multiple 'dist_m' values if a geohash spans the radius boundary.
                 # We'll take the minimum distance for simplicity or reconsider how distance is handled after grouping.
                 # For this pipeline, $geoNear is the first stage, so 'dist_m' is on the documents *before* grouping.
                 # We need to include it in the group stage if we want to use it later. Let's add it.
                 'dist_m':    { '$min': '$dist_m' } # Take the minimum distance within the group
            }
        }
        pipeline.append(group_stage)

        # 3) Project Stage: Reshape the output documents and calculate the average intensity
        project_stage = {
            '$project': {
                '_id':      0,          # Exclude the default _id field
                'geohash':  '$_id',     # Rename _id to geohash
                # Extract latitude and longitude from the center point coordinates
                'lat':      { '$arrayElemAt': ['$center.coordinates', 1] }, # latitude is the second element (index 1)
                'lon':      { '$arrayElemAt': ['$center.coordinates', 0] }, # longitude is the first element (index 0)
                'count':    1,          # Include the total count for the geohash
                'intensity': {          # Calculate the average intensity
                    '$cond': [
                        { '$gt': ['$count', 0] },        # If count is greater than 0
                        { '$divide': ['$sum_noise', '$count'] }, # Divide sum_noise by count
                        0                                # Otherwise, intensity is 0
                    ]
                },
                'distance_m': '$dist_m' # Include the distance (using the min distance from the group stage)
            }
        }
        pipeline.append(project_stage)

        # Execute the aggregation pipeline
        return list(mongo.db.aggregated_measurements.aggregate(pipeline))



class RawMeasurementRepository:
    @staticmethod
    def insert_raw_measurement(user_id, timestamp, noise_level, location):
        """
        Inserts a single raw noise measurement document into the raw_measurements collection.

        :param user_id: str, the ID of the user making the measurement.
        :param timestamp: datetime, the timestamp of the measurement.
        :param noise_level: float, the measured noise level.
        :param location: dict, containing 'type' and 'coordinates' [lon, lat].
        :return: ObjectId, the ID of the newly inserted document.
        """
        # Generate geohash for the location
        # geohash2 encode expects latitude, longitude
        geohash = Geohash.encode(location['coordinates'][1], location['coordinates'][0], precision=7)

        # Prepare the raw measurement document
        measurement = {
            'user_id': ObjectId(user_id), # Assuming user_id here is a string representation of ObjectId
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': geohash,
            # Duration is missing in this specific method, but present in process_measurement.
            # Adding it for consistency if this method is intended for the same type of data.
            # 'duration': duration, # Uncomment and pass duration if needed
        }
        # Insert the document and return the inserted ID
        result = mongo.db.raw_measurements.insert_one(measurement)
        return result.inserted_id

    @staticmethod
    def get_high_exposure(user_id):
        """
        Returns the total duration of high noise level exposures for a specific user.
        :param user_id: str or ObjectId, the user's ID.
        :return: total duration (int) of high exposure.
        """
        # Convert user_id to ObjectId if it's a string
        if isinstance(user_id, str):
            try:
                user_id = ObjectId(user_id)
            except Exception:
                pass  # If conversion fails, keep it as is (e.g., might be a username)

        # Define query for high noise levels (above 70 dB)
        query = {
            'user_id': user_id,
            'noise_level': { '$gt': 70 }
        }

        # Use aggregation to sum durations of matching records
        pipeline = [
            { '$match': query },
            { '$group': { '_id': None, 'total_duration': { '$sum': '$duration' } } }
        ]

        result = list(mongo.db.raw_measurements.aggregate(pipeline))

        # Return the total duration if results are found, otherwise 0
        return result[0]['total_duration'] if result else 0


    @staticmethod
    def get_low_exposure(user_id):
        """
        Returns the total duration of low noise level exposures for a specific user.
        :param user_id: str or ObjectId, the user's ID.
        :return: total duration (int) of low exposure.
        """
        # Convert user_id to ObjectId if it's a string
        if isinstance(user_id, str):
            try:
                user_id = ObjectId(user_id)
            except Exception:
                pass  # If conversion fails, keep it as is (e.g., might be a username)

        # Define query for low noise levels (below 50 dB)
        query = {
            'user_id': user_id,
            'noise_level': { '$lt': 50 }
        }

        # Use aggregation to sum durations of matching records
        pipeline = [
            { '$match': query },
            { '$group': { '_id': None, 'total_duration': { '$sum': '$duration' } } }
        ]

        result = list(mongo.db.raw_measurements.aggregate(pipeline))

        # Return the total duration if results are found, otherwise 0
        return result[0]['total_duration'] if result else 0

