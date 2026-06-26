package com.example.data.network.wallet


import com.example.data.network.wallet.model.TotalBalanceResponse
import com.example.data.network.wallet.model.WalletBalanceResponse
import com.example.data.network.wallet.model.WalletCreateRequest
import com.example.data.network.wallet.model.WalletMemberResponse
import com.example.data.network.wallet.model.WalletMemberRoleUpdateRequest
import com.example.data.network.wallet.model.WalletResponse
import com.example.data.network.wallet.model.WalletShareRequest
import com.example.data.network.wallet.model.WalletUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface WalletApi {
    @GET("api/wallets/")
    suspend fun getWallets(): List<WalletResponse>

    @POST("api/wallets/")
    suspend fun createWallet(@Body request: WalletCreateRequest): WalletResponse

    @GET("api/wallets/user/total")
    suspend fun getTotalBalance(): TotalBalanceResponse

    @GET("api/wallets/{wallet_id}")
    suspend fun getWalletDetail(@Path("wallet_id") walletId: Int): WalletResponse

    @DELETE("api/wallets/{wallet_id}")
    suspend fun deleteWallet(@Path("wallet_id") walletId: Int): Response<Unit>

    @PUT("api/wallets/{wallet_id}") suspend fun updateWallet(
        @Path("wallet_id") walletId: Int,
        @Body walletRequest: WalletUpdateRequest
    ): Response<WalletResponse>

    @GET("api/wallets/{wallet_id}/balance")
    suspend fun getWalletBalance(@Path("wallet_id") walletId: Int): WalletBalanceResponse

    @GET("api/wallets/shared")
    suspend fun getSharedWallets(): List<WalletResponse>

    @GET("api/wallets/{wallet_id}/members")
    suspend fun getWalletMembers(@Path("wallet_id") walletId: Int): List<WalletMemberResponse>

    @POST("api/wallets/{wallet_id}/share")
    suspend fun shareWallet(
        @Path("wallet_id") walletId: Int,
        @Body request: WalletShareRequest
    ): Response<WalletMemberResponse>

    @PUT("api/wallets/{wallet_id}/members/{user_id}")
    suspend fun updateMemberRole(
        @Path("wallet_id") walletId: Int,
        @Path("user_id") userId: Int,
        @Body request: WalletMemberRoleUpdateRequest
    ): Response<WalletMemberResponse>

    @DELETE("api/wallets/{wallet_id}/members/{user_id}")
    suspend fun removeMember(
        @Path("wallet_id") walletId: Int,
        @Path("user_id") userId: Int
    ): Response<Unit>
}