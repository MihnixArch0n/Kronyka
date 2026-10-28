package example.kronyka.domain.service

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DutchPriceCalculatorTest {

    private val calculator = DutchPriceCalculator()

    private val startTime =
        Instant.parse("2026-10-27T10:00:00Z")

    private fun priceAt(seconds: Long): Long {
        return calculator.calculatePrice(
            startPrice = 10_000_000L,
            floorPrice = 5_000_000L,
            stepDecrement = 500_000L,
            stepIntervalSeconds = 60L,
            startTime = startTime,
            currentTime = startTime + kotlin.time.Duration.parse("${seconds}s")
        )
    }

    @Test
    fun `initial price equals start price`() {
        assertEquals(10_000_000L, priceAt(0))
    }

    @Test
    fun `price does not decrease before first interval`() {
        assertEquals(10_000_000L, priceAt(59))
    }

    @Test
    fun `price decreases after one interval`() {
        assertEquals(9_500_000L, priceAt(60))
    }

    @Test
    fun `price decreases after multiple intervals`() {
        assertEquals(8_500_000L, priceAt(180))
    }

    @Test
    fun `price reaches floor price`() {
        assertEquals(5_000_000L, priceAt(600))
    }

    @Test
    fun `price never falls below floor price`() {
        assertEquals(5_000_000L, priceAt(3600))
    }

    @Test
    fun `price remains at start price before auction starts`() {
        assertEquals(10_000_000L, priceAt(-60))
    }

    @Test
    fun `rejects invalid start price`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.calculatePrice(
                startPrice = 4_000_000L,
                floorPrice = 5_000_000L,
                stepDecrement = 500_000L,
                stepIntervalSeconds = 60L,
                startTime = startTime,
                currentTime = startTime
            )
        }
    }

    @Test
    fun `rejects zero step interval`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.calculatePrice(
                startPrice = 10_000_000L,
                floorPrice = 5_000_000L,
                stepDecrement = 500_000L,
                stepIntervalSeconds = 0L,
                startTime = startTime,
                currentTime = startTime
            )
        }
    }

    @Test
    fun `price changes exactly at step boundary`() {
        fun priceAtTime(time: String): Long =
            calculator.calculatePrice(
                startPrice = 10_000_000L,
                floorPrice = 5_000_000L,
                stepDecrement = 500_000L,
                stepIntervalSeconds = 60L,
                startTime = startTime,
                currentTime = Instant.parse(time)
            )

        // Chưa đủ 60 giây: giá không giảm
        assertEquals(
            10_000_000L,
            priceAtTime("2026-10-27T10:00:59.999Z")
        )

        // Đúng 60 giây: giảm một bậc
        assertEquals(
            9_500_000L,
            priceAtTime("2026-10-27T10:01:00Z")
        )

        // Chưa đủ 120 giây: giữ nguyên
        assertEquals(
            9_500_000L,
            priceAtTime("2026-10-27T10:01:59.999Z")
        )

        // Đúng 120 giây: giảm hai bậc
        assertEquals(
            9_000_000L,
            priceAtTime("2026-10-27T10:02:00Z")
        )
    }

    @Test
    fun `price stays at floor after reaching minimum`() {
        assertEquals(5_000_000L, priceAt(600))
        assertEquals(5_000_000L, priceAt(601))
        assertEquals(5_000_000L, priceAt(660))
        assertEquals(5_000_000L, priceAt(3600))
    }

}
