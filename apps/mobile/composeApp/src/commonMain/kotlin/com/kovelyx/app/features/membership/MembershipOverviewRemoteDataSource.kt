package com.kovelyx.app.features.membership

/** Independent accounts do not expose the retired subscription RPC. */
internal object MembershipOverviewRemoteDataSource {
    suspend fun getMembershipOverview(): MembershipOverview = MembershipOverview()
}
