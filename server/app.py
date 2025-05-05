import os
from dotenv import load_dotenv
from flask import Flask
from app.extensions import mongo, bcrypt, login_manager

load_dotenv()

def create_app():
    app = Flask(__name__)
    mongo_user = os.getenv('MONGO_USERNAME')
    mongo_pass = os.getenv('MONGO_PASSWORD')
    mongo_host = os.getenv('MONGO_HOST', 'localhost')
    mongo_port = os.getenv('MONGO_PORT', '27017')
    mongo_db = os.getenv('MONGO_DB', 'global')
    mongo_uri = f"mongodb://{mongo_user}:{mongo_pass}@{mongo_host}:{mongo_port}/{mongo_db}?authSource=admin&retryWrites=true&w=majority"
    print(f"Mongo URI: {mongo_uri}")
    app.config['MONGO_URI'] = mongo_uri
    app.config['SECRET_KEY'] = os.getenv('SECRET_KEY')

    mongo.init_app(app)
    bcrypt.init_app(app)
    login_manager.init_app(app)
    login_manager.login_view = 'login'

    from app.routes import bp as main_bp
    app.register_blueprint(main_bp)

    return app

if __name__ == '__main__':
    app = create_app()
    app.run(debug=True, port=5000)
