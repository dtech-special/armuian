package dev.armuian.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiscTest {
    @Test fun checksumFileFormats() {
        val h = "a".repeat(64); val h2 = "b".repeat(64)
        assertEquals(h2, ChecksumParser.find("$h  one.zip\n$h2 *two.zip\n", "two.zip"))
        assertEquals(h, ChecksumParser.find("$h\n", "anything.zip"))
        assertNull(ChecksumParser.find("$h  one.zip\n", "two.zip"))
    }

    @Test fun mobilePolicy() {
        assertEquals(DownloadDecision.NeedMobileConfirmation, NetworkPolicy.decide(MobileDataPolicy.ASK, ConnectionType.MOBILE))
        assertEquals(DownloadDecision.BlockedWifiOnly, NetworkPolicy.decide(MobileDataPolicy.WIFI_ONLY, ConnectionType.MOBILE))
        assertEquals(DownloadDecision.Proceed, NetworkPolicy.decide(MobileDataPolicy.ALLOW, ConnectionType.MOBILE))
        assertEquals(DownloadDecision.Proceed, NetworkPolicy.decide(MobileDataPolicy.ASK, ConnectionType.WIFI))
        assertEquals(DownloadDecision.BlockedNoNetwork, NetworkPolicy.decide(MobileDataPolicy.ALLOW, ConnectionType.NONE))
    }

    @Test fun progressMath() {
        var t = 0L
        val tr = ProgressTracker(total = 1000, startBytes = 200, nowNs = { t })
        t = 2_000_000_000L // 2 c
        val p = tr.update(600, Stage.DOWNLOADING, null)
        assertEquals(200L, p.bytesPerSecond)   // (600-200)/2
        assertEquals(2L, p.etaSeconds)          // (1000-600)/200
        assertEquals(60, p.percent)
    }
}
