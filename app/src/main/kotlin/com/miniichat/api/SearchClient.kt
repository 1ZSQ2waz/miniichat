package com.miniichat.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Optional Brave Search integration. The user supplies a separate Brave Search API key. */
class SearchClient {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SearchResponse(val web: WebResults? = null)
    @Serializable
    private data class WebResults(val results: List<SearchResult> = emptyList())
    @Serializable
    private data class SearchResult(
        val title: String = "",
        val url: String = "",
        val description: String = ""
    )

    /** Returns source text for the model. URLs remain in the answer for user verification. */
    suspend fun search(query: String, apiKey: String): String {
        require(apiKey.isNotBlank()) { "请在设置中填写 Brave Search API Key" }
        val response = client.get("https://api.search.brave.com/res/v1/web/search") {
            headers { append("X-Subscription-Token", apiKey) }
            parameter("q", query.take(500))
            parameter("count", 5)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("联网搜索失败：HTTP ${response.status.value}，请检查 Brave Search API Key")
        }
        val results = json.decodeFromString<SearchResponse>(response.bodyAsText())
            .web?.results.orEmpty().take(5)
        if (results.isEmpty()) return "没有找到相关网页结果。请明确告诉用户本次未找到搜索结果。"
        return buildString {
            append("以下是刚刚检索到的网页摘要。请根据问题引用来源链接；摘要可能不完整，不要声称已阅读全文。\n")
            results.forEachIndexed { index, result ->
                append("[${index + 1}] ${result.title.take(180)}\n")
                append("${result.url}\n")
                append(result.description.take(600))
                append("\n\n")
            }
        }
    }
}
