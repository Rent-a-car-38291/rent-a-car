import com.example.domain.UserRepository
import kotlin.test.*

class UserRepositoryTest {
    @BeforeTest
    fun reset() = UserRepository.clear()

    @Test
    fun RegisterDuplicateEmailReturnsNull() {
        UserRepository.register("Jan", "jan@example.com", "supersecret")

        assertNull(UserRepository.register("Piet", "jan@example.com", "anothersecret"))
    }

    @Test
    fun RegisterComparesEmailIgnoringCase() {
        UserRepository.register("Jan", "jan@example.com", "supersecret")

        assertNull(UserRepository.register("Jan", "JAN@Example.COM", "supersecret"))
    }

    @Test
    fun RegisterStoresHashedPassword() {
        val user = assertNotNull(UserRepository.register("Jan", "jan@example.com", "supersecret"))

        assertNotEquals("supersecret", user.passwordHash)
        assertFalse(user.passwordHash.contains("supersecret"))
    }
}
