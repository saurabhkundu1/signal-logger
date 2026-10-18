package com.example.signallogger.network

import com.example.signallogger.data.SignalAreaSummaryDto
import com.example.signallogger.data.SignalMetricDto
import com.example.signallogger.data.SignalMetricUploadDto
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

class SignalApiClient(var baseUrl: String = "") {

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
                setBody(
                    SignalMetricUploadDto(
                        operatorName = metric.operatorName,
                        signalStrengthDbm = metric.signalStrengthDbm,
                        signalLevel = metric.signalLevel,
                        networkType = metric.networkType,
                        areaCell = metric.areaCell
                    )
                )
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMetricsHistory(): Result<List<SignalAreaSummaryDto>> {
        return try {
            val cleanUrl = baseUrl.trimEnd('/')
            val response: List<SignalAreaSummaryDto> = client.get("$cleanUrl/api/signal-metrics/areas").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() = client.close()

    companion object {
        fun isValidServerUrl(value: String): Boolean = runCatching {
            val uri = java.net.URI(value.trim())
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
    }
}
