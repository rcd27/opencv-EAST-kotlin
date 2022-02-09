import nu.pattern.OpenCV
import org.junit.jupiter.api.BeforeAll

interface OpenCVTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadOpenCV() {
            OpenCV.loadLocally()
        }
    }
}
