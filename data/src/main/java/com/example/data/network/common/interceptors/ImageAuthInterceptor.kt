package com.example.data.network.common.interceptors

import android.util.Log
import com.example.domain.accessToken.AccessTokenRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Bearer auth for image/static requests without forcing Accept: application/json,
 * which can prevent some servers from returning image bytes to Coil.
 */
class ImageAuthInterceptor(
    private val accessTokenRepository: AccessTokenRepository
) : Interceptor {
  companion object {
      private const val TAG = "MoneyMateAvatar"
  }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        val token = runBlocking { accessTokenRepository.getAccessToken() }
        val hasToken = !token.isNullOrBlank()
        if (hasToken) {
            builder.header(HeadersInterceptor.HEADER_AUTH, "${HeadersInterceptor.HEADER_BEARER} $token")
        }
        builder.header("Accept", "image/*,*/*")
        Log.d(TAG, "Image request ${request.url} auth=$hasToken")
        val response = chain.proceed(builder.build())
        Log.d(TAG, "Image response ${request.url} -> ${response.code}")
        return response
    }
}
