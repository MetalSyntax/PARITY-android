package com.metalsyntax.paritykt.ui


import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTest {
    @Test fun replaceKeepsCountAndSwapsDuplicates() {
        val q = listOf(Route.TX, Route.BUDGET, Route.PLAN)
        assertEquals(listOf(Route.GOALS, Route.BUDGET, Route.PLAN), swapQuick(q, Route.TX, Route.GOALS))
        assertEquals(listOf(Route.PLAN, Route.BUDGET, Route.TX), swapQuick(q, Route.TX, Route.PLAN))
    }
}
