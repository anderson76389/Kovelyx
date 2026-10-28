package com.nuvio.tv.data.remote.cloudflare

import com.nuvio.tv.domain.model.MemberAccess
import javax.inject.Inject
import javax.inject.Singleton

/** No old membership RPC is available on Kovelyx Cloudflare. Never query an unrelated account backend. */
@Singleton
class MemberAccessRemoteDataSource @Inject constructor() {
    suspend fun getMemberAccess(): MemberAccess = MemberAccess.None
}
