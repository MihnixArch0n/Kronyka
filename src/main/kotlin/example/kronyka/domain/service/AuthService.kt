package example.kronyka.domain.service

import example.kronyka.domain.model.User

interface AuthService {
    suspend fun register(username: String, password: String, fullName: String): User
    suspend fun login(username: String, password: String): User
}

