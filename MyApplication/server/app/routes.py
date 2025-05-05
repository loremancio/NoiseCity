from flask import Blueprint, request, jsonify
from flask_login import login_user, logout_user, login_required, current_user
from app.repository import UserRepository
from app.extensions import login_manager

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
