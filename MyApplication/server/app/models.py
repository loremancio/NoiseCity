from flask_login import UserMixin
from bson import ObjectId
from app.extensions import mongo, bcrypt

class User(UserMixin):
    def __init__(self, user_data):
        self.id = str(user_data['_id'])
        self.username = user_data['username']
        self.password_hash = user_data['password']
        self.count = user_data.get('count', 0)
        self.achievements = user_data.get('achievements', [])

    @staticmethod
    def from_mongo(user_data):
        if user_data:
            return User(user_data)
        return None

    @staticmethod
    def get_by_username(username):
        user_data = mongo.db.users.find_one({'username': username})
        if user_data:
            return User(user_data)
        return None

    @staticmethod
    def create_user(username, password):
        if mongo.db.users.find_one({'username': username}):
            return None  # Username already exists
        pw_hash = bcrypt.generate_password_hash(password).decode('utf-8')
        user_id = mongo.db.users.insert_one({'username': username, 'password': pw_hash}).inserted_id
        return User({'_id': user_id, 'username': username, 'password': pw_hash})

    def check_password(self, password):
        return bcrypt.check_password_hash(self.password_hash, password)
