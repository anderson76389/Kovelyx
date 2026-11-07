package com.kovelyx.app.features.membership

/**
 * There is no supporter entitlement service on the independent Kovelyx cloud.
 * Do not query a legacy backend or import its paid membership state.
 */
internal object MemberAccessRemoteDataSource {
    suspend fun getMemberAccess(): MemberAccess = MemberAccess.None
}
