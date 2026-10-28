package com.nuvio.tv.data.remote.cloudflare

import com.nuvio.tv.domain.model.MembershipOverview
import javax.inject.Inject
import javax.inject.Singleton

/** Cloudflare accounts are independent: legacy membership status must not be reused. */
@Singleton
class MembershipOverviewRemoteDataSource @Inject constructor() {
    suspend fun getMembershipOverview(): MembershipOverview = MembershipOverview()
}
