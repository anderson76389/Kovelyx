package com.nuvio.app.features.membership

/** Do not fetch the old account's subscription state from another provider. */
internal object MembershipOverviewRemoteDataSource {
    suspend fun getMembershipOverview(): MembershipOverview = MembershipOverview()
}
