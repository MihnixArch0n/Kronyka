package example.kronyka.presentation.controller

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ScalarOpenApiDocsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `v3 api-docs endpoint returns valid OpenAPI JSON schema with Kronyka info and security`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Kronyka Auction API"))
            .andExpect(jsonPath("$.info.version").value("0.0.1"))
            .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth").exists())
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
    }

    @Test
    fun `v3 api-docs endpoint strictly distinguishes public from protected endpoint security scopes`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            // Global security MUST NOT be present at root level
            .andExpect(jsonPath("$.security").doesNotExist())
            // 5 Public Endpoints MUST NOT have security requirement
            .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/auctions'].get.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].get.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get.security").doesNotExist())
            // 7 Protected Endpoints MUST require BearerAuth
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions'].post.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.security[0].BearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.security[0].BearerAuth").exists())
    }

    @Test
    fun `v3 api-docs endpoint documents all 12 implemented endpoints with paths and methods`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            // Auth endpoints
            .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
            // Auction endpoints (Vickrey & Common)
            .andExpect(jsonPath("$.paths['/api/auctions'].get").exists())
            .andExpect(jsonPath("$.paths['/api/auctions'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post").exists())
            // Auction endpoints (Dutch)
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post").exists())
    }

    @Test
    fun `v3 api-docs endpoint documents detailed error status codes across all implemented endpoints`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            // Auth errors
            .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['404']").exists())

            // Auction list & create errors
            .andExpect(jsonPath("$.paths['/api/auctions'].get.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions'].post.responses['401']").exists())

            // Auction details & cancel errors
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].get.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].get.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['409']").exists())

            // Commit & reveal errors
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.responses['404']").exists())

            // Dutch auction errors
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['409']").exists())
    }

    @Test
    fun `v3 api-docs endpoint embeds specific error examples for Scalar interactive inspection`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            // Verify specific example objects in responses
            .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['409'].content['application/json'].examples['UserAlreadyExists']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.responses['409'].content['application/json'].examples['CancellationFailed']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.responses['400'].content['application/json'].examples['InvalidDeposit']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.responses['400'].content['application/json'].examples['InvalidHashFormat']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.responses['400'].content['application/json'].examples['RevealMismatch']").exists())
            // Dutch auction error examples
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.responses['400'].content['application/json'].examples['InvalidFloorPrice']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.responses['400'].content['application/json'].examples['InvalidTimeline']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get.responses['400'].content['application/json'].examples['NotDutchAuction']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['400'].content['application/json'].examples['SelfPurchase']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['400'].content['application/json'].examples['NotActive']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['400'].content['application/json'].examples['OutsideWindow']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['400'].content['application/json'].examples['NotDutchAuction']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['409'].content['application/json'].examples['VersionConflict']").exists())
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.responses['409'].content['application/json'].examples['AlreadySold']").exists())
    }

    @Test
    fun `v3 api-docs endpoint labels operation summaries with engine prefixes for Scalar navigation`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auctions'].get.summary").value("[Common] List auctions"))
            .andExpect(jsonPath("$.paths['/api/auctions'].post.summary").value("[Vickrey Engine] Create a new Vickrey auction"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].get.summary").value("[Common] Get auction details"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}'].delete.summary").value("[Common] Cancel an auction"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/commit'].post.summary").value("[Vickrey Engine] Commit a blind bid (Phase 1)"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/reveal'].post.summary").value("[Vickrey Engine] Reveal a bid (Phase 2)"))
            .andExpect(jsonPath("$.paths['/api/auctions/dutch'].post.summary").value("[Dutch Engine] Create a new Dutch auction"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/price'].get.summary").value("[Dutch Engine] Get current Dutch auction price"))
            .andExpect(jsonPath("$.paths['/api/auctions/{id}/buy-dutch'].post.summary").value("[Dutch Engine] Buy a Dutch auction at current price"))
    }

    @Test
    fun `v3 api-docs endpoint formats Kotlin Uuid and Instant as proper OpenAPI scalar formats`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.schemas['CreateVickreyAuctionRequest'].properties.start_time.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas['CreateAuctionResponse'].properties.auction_id.format").value("uuid"))
            .andExpect(jsonPath("$.components.schemas['AuctionDetailResponse'].properties.created_at.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas['AuctionDetailResponse'].properties.seller_id.format").value("uuid"))
    }

    @Test
    fun `scalar endpoint returns 200 OK HTML page referencing local api-docs and js bundle`() {
        val htmlResult = mockMvc.perform(get("/scalar"))
            .andExpect(status().isOk)
            .andReturn()

        val htmlContent = htmlResult.response.contentAsString
        htmlContent shouldContain "<div id=\"app\"></div>"
        htmlContent shouldContain "Scalar.createApiReference"
        htmlContent shouldContain "<title>Kronyka Auction API Reference</title>"
        htmlContent shouldContain "\"/v3/api-docs\""

        val jsResult = mockMvc.perform(get("/scalar/scalar.js"))
            .andExpect(status().isOk)
            .andReturn()

        jsResult.response.contentType shouldContain "application/javascript"
    }
}
