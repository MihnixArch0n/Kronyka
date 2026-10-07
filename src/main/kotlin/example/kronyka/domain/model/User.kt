package example.kronyka.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User(

    @Column(name = "username", nullable = false, unique = true, length = 50)
    var username: String,

    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,

    @Column(name = "full_name", nullable = false, length = 100)
    var fullName: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    val id: Int? = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)