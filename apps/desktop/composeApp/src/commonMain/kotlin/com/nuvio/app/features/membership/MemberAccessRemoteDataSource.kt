package com.nuvio.app.features.membership

/** No membership entitlement service is deployed in the independent Worker. */
internal object MemberAccessRemoteDataSource {
    suspend fun getMemberAccess(): MemberAccess = MemberAccess.None
}
