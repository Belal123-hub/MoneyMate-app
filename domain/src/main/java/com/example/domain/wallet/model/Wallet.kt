package com.example.domain.wallet.model

data class Wallet(
    val id: Int,
    val name: String,
    val currency: String = "USD",
    val walletType: String,
    val initialBalance: String = "0.00",
    val cardNumber: String? = null,
    val color: String = "#4D6BFA",
    val balance: String? = null,
    val userId: Int? = null,
    val ownerUserId: Int = 0,
    val isShared: Boolean = false,
    val myRole: String? = null,
    val memberCount: Int = 0,
    val createdAt: String? = null,
    /** False when created/updated locally and not yet pushed to the server. */
    val isSynced: Boolean = true
) {
    /**
     * Show shared UI when the wallet has another member besides the owner,
     * or when the current user joined as editor/viewer.
     */
    val showsSharedUi: Boolean
        get() {
            val role = myRole?.lowercase()
            if (role == "editor" || role == "viewer") return true
            return memberCount > 1
        }

    private val normalizedRole: String?
        get() = myRole?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isViewOnly(): Boolean = normalizedRole == "viewer"

    fun canAddTransactions(): Boolean = !isViewOnly()

    fun canEditWallet(): Boolean = when (normalizedRole) {
        "viewer" -> false
        "editor" -> true
        "admin" -> true
        null -> !isShared || (ownerUserId > 0 && userId != null && ownerUserId == userId)
        else -> true
    }

    fun canManageMembers(): Boolean =
        normalizedRole == "admin" ||
            (ownerUserId > 0 && userId != null && ownerUserId == userId) ||
            (!isShared && normalizedRole.isNullOrBlank())

    /** Role for UI/permissions; falls back when API omits my_role but user is a collaborator. */
    fun withResolvedRole(role: String?): Wallet {
        if (role.isNullOrBlank() || !myRole.isNullOrBlank()) return this
        return copy(myRole = role)
    }
}
