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
    fun `v3 api-docs endpoint formats Kotlin Uuid and Instant as proper OpenAPI scalar formats`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.schemas['CreateVickreyAuctionRequest'].properties.startTime.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas['CreateAuctionResponse'].properties.auctionId.format").value("uuid"))
            .andExpect(jsonPath("$.components.schemas['AuctionDetailResponse'].properties.createdAt.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas['AuctionDetailResponse'].properties.sellerId.format").value("uuid"))
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
