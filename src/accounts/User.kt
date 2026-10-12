package com.example.accounts

import kotlinx.serialization.Serializable

class User(
    val id: Int,
    val name: String,
    val email: String,
    val passwordHash: String
)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

// The password hash is deliberately not part of this type, so it can never be serialized.
@Serializable
data class UserResponse(val id: Int, val name: String, val email: String)

@Serializable
data class UpdateAccountRequest(val name: String, val email: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

fun User.toResponse() = UserResponse(id, name, email)
