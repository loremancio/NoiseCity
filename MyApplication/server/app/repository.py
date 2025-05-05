from app.extensions import mongo
from app.models import User
from bson import ObjectId
from app.extensions import bcrypt

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
