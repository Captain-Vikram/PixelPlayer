package com.theveloper.pixelplay.data.update

import android.content.Context
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppUpdateVersionComparisonTest {

    private lateinit var appUpdateManager: AppUpdateManager
    private val context: Context = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)

    @Before
    fun setUp() {
        appUpdateManager = AppUpdateManager(context, okHttpClient)
    }

    @Test
    fun `isVersionNewer returns true when remote semver is higher`() {
        assertTrue(appUpdateManager.isVersionNewer("1.1.0", "1.0.0"))
        assertTrue(appUpdateManager.isVersionNewer("v2.0.0", "1.9.9"))
        assertTrue(appUpdateManager.isVersionNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `isVersionNewer returns false when remote semver is lower`() {
        assertFalse(appUpdateManager.isVersionNewer("1.0.0", "1.1.0"))
        assertFalse(appUpdateManager.isVersionNewer("v0.9.0", "1.0.0"))
    }

    @Test
    fun `isVersionNewer returns false when versions are identical`() {
        assertFalse(appUpdateManager.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(appUpdateManager.isVersionNewer("v1.0.0", "1.0.0"))
        assertFalse(appUpdateManager.isVersionNewer("v1.0.0-20260926-b3e92de", "1.0.0-20260926-b3e92de"))
    }

    @Test
    fun `isVersionNewer detects newer dated build when installed is base semver`() {
        // This was the bug: base 1.0.0 without date did not detect dated nightly/fork releases
        assertTrue(appUpdateManager.isVersionNewer("v1.0.0-20260926-b3e92de", "1.0.0"))
        assertTrue(appUpdateManager.isVersionNewer("v1.0.0-20260926-b3e92de", "v1.0.0"))
    }

    @Test
    fun `isVersionNewer compares dates accurately when semvers are equal`() {
        assertTrue(appUpdateManager.isVersionNewer("v1.0.0-20260927-b3e92de", "v1.0.0-20260926-e7b3643"))
        assertFalse(appUpdateManager.isVersionNewer("v1.0.0-20260925-b3e92de", "v1.0.0-20260926-e7b3643"))
    }

    @Test
    fun `isVersionNewer detects differing commit hash on same date`() {
        assertTrue(appUpdateManager.isVersionNewer("v1.0.0-20260926-b3e92de", "v1.0.0-20260926-e7b3643"))
    }
}
