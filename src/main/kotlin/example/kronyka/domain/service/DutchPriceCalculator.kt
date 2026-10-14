
package example.kronyka.domain.service

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class DutchPriceCalculator {

    fun calculatePrice(
        startPrice: BigDecimal,
        floorPrice: BigDecimal,
        stepDecrement: BigDecimal,
        stepIntervalSeconds: Long,
        startTime: Instant,
        currentTime: Instant
    ): BigDecimal {

        // Kiem tra du lieu dau vao
        require(startPrice >= floorPrice) {
            "Start price must be >= floor price"
        }
        require(floorPrice >= BigDecimal.ZERO) {
            "Floor price must not be negative"
        }
        require(stepDecrement > BigDecimal.ZERO) {
            "Step decrement must be positive"
        }
        require(stepIntervalSeconds > 0) {
            "Step interval must be positive"
        }

        // Tinh so giay da troi qua
        val elapsedSeconds = Duration.between(
            startTime,
            currentTime
        ).seconds.coerceAtLeast(0)

        // Tinh so nac gia da giam
        val steps = elapsedSeconds / stepIntervalSeconds

        // Tinh tong so tien giam
        val discount = stepDecrement.multiply(
            BigDecimal.valueOf(steps)
        )

        // Tinh gia hien tai
        val currentPrice = startPrice.subtract(discount)

        // Khong cho gia thap hon gia san
        return currentPrice.max(floorPrice)
    }
}
