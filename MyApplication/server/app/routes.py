from flask import Blueprint, request, jsonify
from flask_login import login_user, logout_user, login_required, current_user
from app.repository import UserRepository, MeasurementRepository
from app.extensions import login_manager
from datetime import datetime

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
#@login_required
def add_measurement():
    try:
        data = request.get_json()

        # Validazione dei dati
        required_fields = ["user_id", "timestamp", "noise_level", "location"]
        if not all(field in data for field in required_fields):
            return jsonify({"error": "Missing required fields"}), 400

        # Validazione del formato dei dati
        if not isinstance(data["location"], dict) or "type" not in data["location"] or "coordinates" not in data["location"]:
            return jsonify({"error": "Invalid location format"}), 400

        # Preparazione dei dati per il database
        measurement = {
            "user_id": data["user_id"],
            "timestamp": datetime.fromisoformat(data["timestamp"].replace("Z", "+00:00")),
            "noise_level": data["noise_level"],
            "location": data["location"],
        }

        # Inserimento nel database
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