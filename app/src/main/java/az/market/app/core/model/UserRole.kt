package az.market.app.core.model

/**
 * The three roles served by the single APK. The role is ALWAYS read from the server
 * (user_roles table, enforced by RLS) — never chosen or stored by the client as truth.
 */
enum class UserRole {
    CUSTOMER,
    ADMIN,
    COURIER,
    ;

    companion object {
        /** Maps the server value (e.g. "admin") to a role; unknown values yield null (=> no access). */
        fun fromServerValue(value: String?): UserRole? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
