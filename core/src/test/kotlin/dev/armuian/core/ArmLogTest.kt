package dev.armuian.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmLogTest {
    @Test fun keepsAtMost1000Entries() {
        ArmLog.clear()
        repeat(1200) { ArmLog.i("t", "m$it") }
        assertEquals(1000, ArmLog.entries.value.size)
        assertEquals("m1199", ArmLog.entries.value.last().key)
    }

    @Test fun runArmCapturesException() {
        val r = runArm { error("boom") }
        assertTrue(r is ArmResult.Fail && r.message == "boom")
    }
}
