package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftStateTest {

    @Test
    fun `napautus kiertaa pienet yksi iso lukko pienet`() {
        assertEquals(ShiftState.SHIFT, ShiftState.OFF.nextOnTap())
        assertEquals(ShiftState.CAPS, ShiftState.SHIFT.nextOnTap())
        assertEquals(ShiftState.OFF, ShiftState.CAPS.nextOnTap())
    }
}
