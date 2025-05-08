from app.extensions import mongo
from app.models import User
from bson import ObjectId
from app.extensions import bcrypt
import geohash2 as Geohash

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


class MeasurementRepository:
    @staticmethod
    def process_measurement(user_id, timestamp, noise_level, location):
        if not isinstance(location, dict) or 'type' not in location or 'coordinates' not in location:
            raise ValueError("Invalid location format")
        if location['type'] != 'Point' or len(location['coordinates']) != 2:
            raise ValueError("Invalid coordinates format")
        if not isinstance(noise_level, (int, float)):
            raise ValueError("Invalid noise level format")

        #geohash creation
        geohash = Geohash.encode(location['coordinates'][1], location['coordinates'][0], precision=6)

        # Creating the measurement object
        measurement = {
            'user_id': user_id,
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': geohash,
        }
        
        try:
            # Inserting the raw measurement
            mongo.db.raw_measurements.insert_one(measurement)

            # finding the correct document to be updated
            # searching by geohash and time bucket within an hour
            time_bucket = timestamp.replace(minute=0, second=0, microsecond=0)

            #the correct time bucket will be equal to time_bucket
            # and the geohash

            # will be equal to the geohash of the measurement
            # if the measurement is not in the database
            existing_measurement = mongo.db.aggregated_measurements.find_one({
                'geohash': geohash,
                'timestamp': time_bucket,
            })

            
            #if the measurement already exists
            if existing_measurement:
                print("Existing measurement found:", existing_measurement)
                #updating the existing measurement by updating the average value, the total and the measurement count
                new_average = (existing_measurement['average'] * existing_measurement['count'] + noise_level) / (existing_measurement['count'] + 1)
                new_count = existing_measurement['count'] + 1
                new_total = existing_measurement['total'] + noise_level
                mongo.db.aggregated_measurements.update_one(
                    {'_id': existing_measurement['_id']},
                    {'$set': {
                        'average': new_average,
                        'count': new_count,
                        'total': new_total,
                    }}
                )
                print("measurement updated" )
            else:
                print("No existing measurement found, creating a new one.")
                #if the measurement does not exist, creating a new one
                new_measurement = {
                    'geohash': geohash,
                    'timestamp': time_bucket,
                    'average': noise_level,
                    'count': 1,
                    'total': noise_level,
                }
                mongo.db.aggregated_measurements.insert_one(new_measurement)
                print("New measurement inserted:", new_measurement)
            return True
        except Exception as e:
            # Handle the error (e.g., log it, re-raise it, etc.)
            #remove the measurement from the database
            mongo.db.raw_measurements.delete_one({'_id': measurement['_id']})
            print(f"Error processing measurement: {e}")
            raise e


class RawMeasurementRepository:
    @staticmethod
    def insert_raw_measurement(user_id, timestamp, noise_level, location):
        measurement = {
            'user_id': ObjectId(user_id),
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': Geohash.encode(location['coordinates'][1], location['coordinates'][0], precision=6),
        }
        mongo.db.raw_measurements.insert_one(measurement)