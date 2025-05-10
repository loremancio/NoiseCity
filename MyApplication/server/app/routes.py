from flask import Blueprint, request, jsonify
from flask_login import login_user, logout_user, login_required, current_user
from app.repository import UserRepository, MeasurementRepository
from app.extensions import login_manager
from datetime import datetime
from app.utils import get_geohashes_within_radius

bp = Blueprint('main', __name__)

@login_manager.user_loader
def load_user(user_id):
    return UserRepository.get_by_id(user_id)

@bp.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    if not username or not password:
        return jsonify({'error': 'Username and password required'}), 400
    user, error = UserRepository.register(username, password)
    if error:
        return jsonify({'error': error}), 409
    login_user(user)
    return jsonify({'message': 'Registration successful'})

@bp.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    user = UserRepository.authenticate(username, password)
    if user:
        login_user(user)
        return jsonify({'message': 'Login successful'})
    return jsonify({'error': 'Invalid credentials'}), 401

@bp.route('/logout')
@login_required
def logout():
    logout_user()
    return jsonify({'message': 'Logout successful'})

@bp.route('/profile')
@login_required
def profile():
    return jsonify({'username': current_user.username})

@bp.route('/measurements', methods=['POST'])
#@login_requireddef add_measurement():
def add_measurement():
    try:
        data = request.get_json()

        # Data validation
        required_fields = ["user_id", "timestamp", "noise_level", "location"]
        if not all(field in data for field in required_fields):
            return jsonify({"error": "Missing required fields"}), 400

        # Data format validation
        if not isinstance(data["location"], dict) or "type" not in data["location"] or "coordinates" not in data["location"]:
            return jsonify({"error": "Invalid location format"}), 400

        # Prepare data for the database
        measurement = {
            "user_id": data["user_id"],
            "timestamp": datetime.fromisoformat(data["timestamp"].replace("Z", "+00:00")),
            "noise_level": data["noise_level"],
            "location": data["location"],
        }

        # Database insertion
        result = MeasurementRepository.process_measurement(
            measurement["user_id"],
            measurement["timestamp"],
            measurement["noise_level"],
            measurement["location"]
        )

        if result:
            return jsonify({"message": "Measurement added successfully"}), 201
        else:
            return jsonify({"error": "Failed to add measurement"}), 500

    except Exception as e:
        return jsonify({"error": str(e)}), 500


from geolib import geohash

@bp.route('/measurements', methods=['GET'])
def get_measurements():
    try:
        #print("Received GET request for measurements")

        latitude = request.args.get('latitude', type=float)
        longitude = request.args.get('longitude', type=float)
        radius_km = request.args.get('radius', default=5, type=float)
        start_timestamp = request.args.get('start_timestamp', type=int)
        end_timestamp = request.args.get('end_timestamp', type=int)

        print("Received parameters:", latitude, longitude, radius_km, start_timestamp, end_timestamp)

        # Validate coordinates
        if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
            return jsonify({"error": "Invalid coordinates"}), 400
        if radius_km <= 0:
            return jsonify({"error": "Radius must be greater than 0"}), 400

        # Parse timestamps if provided
        start_timestamp = datetime.fromisoformat(start_timestamp.replace("Z", "+00:00")) if start_timestamp else None
        end_timestamp = datetime.fromisoformat(end_timestamp.replace("Z", "+00:00")) if end_timestamp else None

        #print("Received parameters:", latitude, longitude, radius_km, start_timestamp, end_timestamp)
        all_geohashes = get_geohashes_within_radius(latitude, longitude, radius_km, precision=7)
        #print("Geohashes within radius:", all_geohashes)

        # Query the database
        measurements = MeasurementRepository.get_aggregated_measurements(
            all_geohashes, start_timestamp, end_timestamp
        )
        #print("Returned measurements:", measurements)

        # Return the results
        return jsonify(measurements), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500