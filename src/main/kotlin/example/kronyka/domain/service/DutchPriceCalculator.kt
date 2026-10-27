package example.kronyka.domain.service

import kotlin.time.Instant

class DutchPriceCalculator {

    fun calculatePrice(
        startPrice: Long,
        floorPrice: Long,
        stepDecrement: Long,
        stepIntervalSeconds: Long,
        startTime: Instant,
        currentTime: Instant
    ): Long {

        require(startPrice >= floorPrice) {
            "Start price must be >= floor price"
        }

        require(floorPrice >= 0) {
            "Floor price must not be negative"
        }

        require(stepDecrement > 0) {
            "Step decrement must be positive"
        }

        require(stepIntervalSeconds > 0) {
            "Step interval must be positive"
        }

        // Tính số giây đã trôi qua
        val elapsedSeconds =
            (currentTime - startTime).inWholeSeconds.coerceAtLeast(0)

        // Tính số nấc giá đã giảm
        val steps = elapsedSeconds / stepIntervalSeconds

        // Tránh tràn số khi tính tổng tiền giảm
        val maxStepsToFloor =
            (startPrice - floorPrice) / stepDecrement +
                    if ((startPrice - floorPrice) % stepDecrement != 0L) 1L else 0L

        if (steps >= maxStepsToFloor) {
            return floorPrice
        }

        val discount = steps * stepDecrement

        return (startPrice - discount).coerceAtLeast(floorPrice)
    }
}
