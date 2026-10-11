package example.kronyka.domain.model

class UserAlreadyExistsException(
    message: String
) : RuntimeException(message)

class BadCredentialsException(
    message: String = "Invalid username or password"
) : RuntimeException(message)

