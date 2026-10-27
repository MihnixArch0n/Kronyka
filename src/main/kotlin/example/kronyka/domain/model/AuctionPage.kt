package example.kronyka.domain.model

data class AuctionPage(
    val items: List<Auction>,
    val total: Long
)