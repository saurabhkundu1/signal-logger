package com.example.signallogger.network

import com.example.signallogger.data.SignalMetricDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SignalApiClient(var baseUrl: String = "http://10.0.2.2:8080") {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun postSignalMetric(metric: SignalMetricDto): Result<SignalMetricDto> {
        return try {
            val cleanUrl = baseUrl.trimEnd('/')
            val response: SignalMetricDto = client.post("$cleanUrl/api/signal-metrics") {
                contentType(ContentType.Application.Json)
                setBody(metric)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMetricsHistory(): Result<List<SignalMetricDto>> {
        return try {
            val cleanUrl = baseUrl.trimEnd('/')
            val response: List<SignalMetricDto> = client.get("$cleanUrl/api/signal-metrics").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
