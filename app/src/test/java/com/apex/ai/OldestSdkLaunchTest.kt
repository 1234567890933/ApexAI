package com.apex.ai

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** minSdk is 24 — prove the first screen still comes up there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OldestSdkLaunchTest {

    @Test
    fun mainActivityStartsOnApi24() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
