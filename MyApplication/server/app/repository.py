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
        # validazioni invariate…
        geohash = Geohash.encode(location['coordinates'][1],
                                location['coordinates'][0],
                                precision=7)

        # Inserisci il raw
        raw_doc = {
            'user_id': user_id,
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': geohash,
        }
        mongo.db.raw_measurements.insert_one(raw_doc)

        # Calcola il bucket orario
        time_bucket = timestamp.replace(minute=0, second=0, microsecond=0)

        # Usa un solo upsert atomico su sum e count
        try:
            mongo.db.aggregated_measurements.update_one(
                { 'geohash': geohash, 'time_bucket': time_bucket },
                {
                    '$inc': {
                        'sum_noise': noise_level,
                        'count': 1
                    },
                    '$setOnInsert': {
                        # salva il centro della cella al primo inserimento
                        'center': {
                            'type': 'Point',
                            'coordinates': [
                                location['coordinates'][0],
                                location['coordinates'][1]
                            ]
                        }
                    }
                },
                upsert=True
            )
            return True

        except Exception as e:
            # rollback raw insertion
            mongo.db.raw_measurements.delete_one({'_id': raw_doc['_id']})
            raise


    @staticmethod
    def get_aggregated_by_geohash(lat, lon, radius_km, start_ts=None, end_ts=None):
        """
        Retrieve aggregated measurements within a given radius around a point,
        and optional time range, computing the average intensity on the fly.

        :param lat:       float, latitude of the center point
        :param lon:       float, longitude of the center point
        :param radius_km: float, search radius in kilometers
        :param start_timestamp: datetime, inclusive start of time range (optional)
        :param end_timestamp:   datetime, inclusive end of time range (optional)
        :return: List of dicts with keys 'lat', 'lon', 'intensity', 'count', 'distance_m'
        """
        # distanza in metri
        radius_m = radius_km * 1000

        pipeline = []

        # 1) filtro spaziale
        geo_near = {
            '$geoNear': {
                'near': { 'type': 'Point', 'coordinates': [lon, lat] },
                'distanceField': 'dist_m',
                'maxDistance': radius_m,
                'spherical': True
            }
        }
        # opzionale: filtro temporale
        if start_ts or end_ts:
            tb_q = {}
            if start_ts:
                tb_q['$gte'] = start_ts.replace(minute=0, second=0, microsecond=0)
            if end_ts:
                tb_q['$lte'] = end_ts.replace(minute=0, second=0, microsecond=0)
            geo_near['$geoNear']['query'] = { 'time_bucket': tb_q }

        pipeline.append(geo_near)

        # 2) raggruppamento per geohash
        pipeline.append({
            '$group': {
                '_id': '$geohash',
                'sum_noise': { '$sum': '$sum_noise' },
                'count':     { '$sum': '$count' },
                'center':    { '$first': '$center' }
            }
        })

        # 3) proiezione del risultato con media
        pipeline.append({
            '$project': {
                '_id':      0,
                'geohash':  '$_id',
                'lat':      { '$arrayElemAt': ['$center.coordinates', 1] },
                'lon':      { '$arrayElemAt': ['$center.coordinates', 0] },
                'count':    1,
                'intensity': {
                    '$cond': [
                        { '$gt': ['$count', 0] },
                        { '$divide': ['$sum_noise', '$count'] },
                        0
                    ]
                },
                'dist_m': '$dist_m'
            }
        })

        # esecuzione
        return list(mongo.db.aggregated_measurements.aggregate(pipeline))



class RawMeasurementRepository:
    @staticmethod
    def insert_raw_measurement(user_id, timestamp, noise_level, location):
        measurement = {
            'user_id': ObjectId(user_id),
            'timestamp': timestamp,
            'noise_level': noise_level,
            'location': location,
            'geohash': Geohash.encode(location['coordinates'][1], location['coordinates'][0], precision=7),
        }
        mongo.db.raw_measurements.insert_one(measurement)