package com.example.data.network.common

import com.example.data.network.common.interceptors.HeadersInterceptor
import com.example.data.network.common.interceptors.RefreshTokenAuthenticator
import com.example.domain.accessToken.AccessTokenRepository
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Suppress("MagicNumber", "LongParameterList")
object Network {
    // 172.18.111.10
    private const val BASE_URL = "http://10.0.2.2:5143/"

    private const val CONTENT_TYPE = "application/json"

    val okHttpCache: Cache
        get() {
            val cacheDirectory = File("http-cache.tmp")
            val cacheSize = 50 * 1024 * 1024
            return Cache(cacheDirectory, cacheSize.toLong())
        }

    val appJson: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    fun getJsonFactory(json: Json): Converter.Factory =
        json.asConverterFactory(contentType = CONTENT_TYPE.toMediaType())

    fun getLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)

    fun getHeadersInterceptor(
        accessTokenRepository: AccessTokenRepository,
    ): HeadersInterceptor = HeadersInterceptor(
        accessTokenRepository = accessTokenRepository,
    )

    fun getRefreshTokenAuthenticator(
        accessTokenRepository: AccessTokenRepository,
        serializer: Json
    ): RefreshTokenAuthenticator = RefreshTokenAuthenticator(
        apiUrl = BASE_URL,
        serializer = serializer,
        accessTokenRepository = accessTokenRepository,
    )

    /**
     * For DEVELOPMENT ONLY - Disables SSL certificate verification
     * DO NOT use this in production!
     */
    private fun configureUnsafeSsl(clientBuilder: OkHttpClient.Builder): OkHttpClient.Builder {
        try {
            // Create a trust manager that accepts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            clientBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            clientBuilder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return clientBuilder
    }

    fun getHttpClient(
        cache: Cache,
        headersInterceptor: HeadersInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        authenticator: RefreshTokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder().apply {
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(60, TimeUnit.SECONDS)
        writeTimeout(30, TimeUnit.SECONDS)

        cache(cache)

        addInterceptor(headersInterceptor)
        addInterceptor(loggingInterceptor)

        authenticator(authenticator)

        // Configure unsafe SSL for development
        configureUnsafeSsl(this)
    }.build()

    fun getRetrofit(
        client: OkHttpClient,
        converterFactory: Converter.Factory,
    ): Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(BASE_URL)
        .addConverterFactory(converterFactory)
        .build()

    inline fun <reified T> getApi(retrofit: Retrofit): T = retrofit.create(T::class.java)
}