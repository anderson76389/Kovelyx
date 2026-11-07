package com.kovelyx.app.features.notifications

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopEpisodeNotificationTest {
    private fun request(id: String, date: String) = EpisodeReleaseNotificationRequest(id, "Kovelyx", "Test", date, "kovelyx://")
    @Test fun deliversOnlyTodaysDueEpisodesAndDeduplicatesIds() {
        val today = request("today", "2026-11-01")
        val values = listOf(today, today, request("tomorrow", "2026-11-02"), request("old", "2026-10-31"), request("bad", "oops"))
        assertEquals(emptyList(), dueEpisodeReleaseRequests(values, LocalDateTime.of(2026, 11, 1, 8, 59)))
        assertEquals(listOf(today), dueEpisodeReleaseRequests(values, LocalDateTime.of(2026, 11, 1, 9, 0)))
        assertEquals(listOf(today), dueEpisodeReleaseRequests(values, LocalDateTime.of(2026, 11, 1, 21, 30)))
        assertEquals(listOf(values[2]), dueEpisodeReleaseRequests(values, LocalDateTime.of(2026, 11, 2, 9, 0)))
    }
}
