package com.kovelyx.app.core.build

expect object AppVersionPolicy {
    val displayVersionName: String
    val displayVersionCode: Int
    val basedOnVersionName: String?
    val userAgentAppName: String
}
