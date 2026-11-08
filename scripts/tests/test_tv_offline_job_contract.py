#!/usr/bin/env python3
"""Static regression guards for the Android TV launcher cache-only job.

These guards complement, but cannot replace, Gradle unit tests or device tests.
The launcher channel is populated from on-disk CW snapshots and Android TvProvider.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
JOB = ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/core/sync/androidtv/TvChannelRefreshJobService.kt"
SYNC = ROOT / "apps/tv/app/src/main/java/com/kovelyx/tv/core/sync/androidtv/AndroidTvChannelSyncService.kt"


class TvOfflineJobContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.job = JOB.read_text(encoding="utf-8")
        cls.sync = SYNC.read_text(encoding="utf-8")

    def test_cached_reconciliation_is_the_job_work(self):
        self.assertIn("syncService.reconcileFromCache()", self.job)
        self.assertIn("cwEnrichmentCache.getInProgressSnapshot()", self.sync)
        self.assertIn("cwEnrichmentCache.getNextUpSnapshot()", self.sync)

    def test_job_does_not_wait_for_network_to_refresh_local_tv_provider(self):
        self.assertNotIn(".setRequiredNetworkType(", self.job)
        self.assertIn("JobInfo.NETWORK_TYPE_NONE", self.job)
        self.assertIn("scheduler.getPendingJob(JOB_ID_PERIODIC)", self.job)

    def test_periodic_job_remains_persisted_and_idempotent(self):
        self.assertRegex(self.job, r"if \(existing != null && existing\.networkType == JobInfo\.NETWORK_TYPE_NONE\) return")
        self.assertIn(".setPeriodic(PERIODIC_INTERVAL_MS)", self.job)
        self.assertIn(".setPersisted(true)", self.job)
        self.assertIn("scheduler.schedule(job)", self.job)

    def test_boot_refresh_remains_bounded_without_network_dependency(self):
        self.assertIn("fun scheduleImmediate(context: Context)", self.job)
        self.assertIn(".setOverrideDeadline(5_000)", self.job)
        self.assertIn("TvChannelRefreshJobService.schedulePeriodic(context)", self.sync)


if __name__ == "__main__":
    unittest.main()
