package com.kovelyx.maintenance.ui

fun main() {
    val a = "account-a:1"
    val b = "account-b:1"
    check(SyncHealth.describe(a).contains("Aucune réussite"))
    SyncHealth.queued(a)
    check(SyncHealth.describe(a).contains("en attente"))
    val generation = SyncHealth.started(a)
    check(SyncHealth.describe(a).contains("en cours"))
    SyncHealth.queued(a)
    SyncHealth.succeeded(a, generation)
    check(SyncHealth.describe(a).contains("Dernière réussite"))
    check(SyncHealth.describe(a).contains("locale en attente"))
    check(SyncHealth.describe(b).contains("Aucune réussite"))
    val next = SyncHealth.started(a)
    SyncHealth.succeeded(a, next)
    check(SyncHealth.describe(a).contains("Aucune demande en attente"))
    SyncHealth.failed(a)
    check(SyncHealth.describe(a).contains("Échec temporaire"))
    SyncHealth.cancelled(a)
    check(!SyncHealth.describe(a).contains("en cours"))
    check(SyncHealth.describe("account-a:2").contains("Aucune réussite"))
    for (i in 1..100) SyncHealth.queued("guest:$i")
    check(SyncHealth.describe(a).contains("Aucune réussite"))
    println("PASS synchronization diagnostics: isolation, concurrent edits, retry, cancellation, bounded history")
}
