from flask import Blueprint, request, jsonify
from flask_login import login_user, logout_user, login_required, current_user
from app.repository import UserRepository, MeasurementRepository
from app.extensions import login_manager
from datetime import datetime
from app.utils import get_geohashes_within_radius
from logging import log

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

@bp.route('/profile', methods=['GET'])
#@login_required
def profile():
    """
    this method should return the user profile, including the username and their achievements
    """
    username = current_user.username if current_user.is_authenticated else request.args.get('username')

    if username:
        user = UserRepository.get_by_username(username)
        if user:
            return jsonify({
                'username': user.username,
                'achievements': user.achievements
            })
        else:
            return jsonify({'error': 'User not found'}), 404
    else:
        # If no username is provided, return the current user's profile
        user = UserRepository.get_by_id(current_user.id)
        if user:
            return jsonify({
                'username': user.username,
                'achievements': user.achievements
            })
        else:
            # If the user is not found in the database, return an error
            log.error(f"User with ID {current_user.id} not found")
    return jsonify({'error': 'User not found'}), 404

@bp.route('/upload', methods=['POST'])
def upload():
    data = request.get_json()
    print(f"Received data")
    print(data)

    return jsonify({'message': 'Audio data received successfully'})

@bp.route('/measurements', methods=['POST'])
#@login_requireddef add_measurement():
def add_measurement():

    print("Received data", request)
    try:
        data = request.get_json()

        # Data validation
        required_fields = ["user_id", "timestamp", "noise_level", "location"]
        if not all(field in data for field in required_fields):
            log.error(f"Missing required fields: {required_fields}")
            return jsonify({"error": "Missing required fields"}), 400

        # Data format validation
        if not isinstance(data["location"], dict) or "type" not in data["location"] or "coordinates" not in data["location"]:
            log.error(f"Invalid location format: {data['location']}")
            return jsonify({"error": "Invalid location format"}), 400

        # Prepare data for the database
        measurement = {
            "user_id": data["user_id"],
            "timestamp": datetime.fromisoformat(data["timestamp"].replace("Z", "+00:00")),
            "noise_level": data["noise_level"],
            "location": data["location"],
            "duration": data.get("duration", 0),
        }

        # Database insertion
        result = MeasurementRepository.process_measurement(
            measurement["user_id"],
            measurement["timestamp"],
            measurement["noise_level"],
            measurement["location"],
            measurement["duration"]
        )

        print(f"Result of measurement processing: {result}")

        if result:
            return jsonify(result), 201
        else:
            return jsonify({"error": "Failed to add measurement"}), 500

    except Exception as e:
        return jsonify({"error": str(e)}), 500



@bp.route('/measurements', methods=['GET'])
def get_measurements():
    try:
        latitude = request.args.get('latitude', type=float)
        longitude = request.args.get('longitude', type=float)
        radius_km = request.args.get('radius', type=float, default=1.0)
        start_ts_str = request.args.get('start_timestamp')
        end_ts_str   = request.args.get('end_timestamp')

        if latitude is None or longitude is None or radius_km is None:
            return jsonify({"error": "Missing required query parameters"}), 400
        if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
            return jsonify({"error": "Invalid coordinates"}), 400
        if radius_km <= 0:
            return jsonify({"error": "Radius must be > 0"}), 400

        # 3) Parse optional timestamps
        start_ts = None
        end_ts   = None
        if start_ts_str:
            try:
                start_ts = datetime.fromisoformat(start_ts_str.replace("Z", "+00:00"))
            except ValueError:
                return jsonify({"error": "Invalid start_timestamp format"}), 400
        if end_ts_str:
            try:
                end_ts = datetime.fromisoformat(end_ts_str.replace("Z", "+00:00"))
            except ValueError:
                return jsonify({"error": "Invalid end_timestamp format"}), 400

        # 4) Fetch aggregated measurements by geohash
        measurements = MeasurementRepository.get_aggregated_by_geohash(
            lat=latitude,
            lon=longitude,
            radius_km=radius_km,
            start_ts=start_ts,
            end_ts=end_ts
        )

        # 5) Return JSON
        return jsonify(measurements), 200

    except Exception as e:
        # Log the error server‐side as needed
        return jsonify({"error": "Server error", "details": str(e)}), 500
