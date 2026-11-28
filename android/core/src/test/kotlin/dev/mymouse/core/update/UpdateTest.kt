package dev.mymouse.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun v(text: String) = AppVersion.parse(text)!!

private fun fixture(): List<ReleaseInfo> =
    Releases.parse(AppVersionTest::class.java.getResource("/github-releases.json")!!.readText())

private fun release(tag: String) =
    Releases.parse("""[{"tag_name":"$tag","draft":false,"prerelease":true,"name":"$tag","body":"","html_url":"https://x","assets":[]}]""").single()

class AppVersionTest {
    @Test
    fun ordersVersions() {
        for ((lower, higher) in listOf(
            "5.0" to "5.0.1", "5.0.1" to "5.1", "5.1" to "10.0", "4.8.3" to "5.0", "9.9" to "10.0", "5.0.9" to "5.0.10", "0.0-dev.3" to "5.0",
        )) {
            assertTrue(v(lower) < v(higher), "$lower < $higher")
            assertTrue(v(higher) > v(lower))
            assertFalse(v(lower) >= v(higher))
        }
    }

    @Test
    fun sameNumbers() {
        for ((a, b) in listOf("5.0" to "5.0.0", "v5.0" to "5.0", "5.0" to "5.0.0.0", "5.1+abc123" to "5.1")) {
            assertTrue(v(a).sameNumbers(v(b)), "$a == $b")
            assertEquals(v(a), v(b))
        }
    }

    @Test
    fun detectsDevVersions() {
        for (text in listOf("0.0-dev.42", "0.0-dev", "0.0", "5.1-dev.7")) assertTrue(v(text).isDev, text)
        for (text in listOf("5.0", "5.0.1", "v5.1")) assertFalse(v(text).isDev, text)
    }

    @Test
    fun rejectsGarbage() {
        for (text in listOf(null, "", "abc", "5", "5.x", "99999999999.1")) assertNull(AppVersion.parse(text), "$text")
    }

    @Test
    fun parsesTagsAndIgnoresOthers() {
        assertEquals("5.0", AppVersion.parseTag("v5.0")!!.display)
        assertEquals("5.0.1", AppVersion.parseTag("v5.0.1")!!.display)
        assertEquals("10.0", AppVersion.parseTag("v10.0")!!.display)
        for (tag in listOf("5.0", "nightly", "v5.1-rc1", "v5", "", null)) assertNull(AppVersion.parseTag(tag), "$tag")
    }
}

class ReleasesTest {
    @Test
    fun parsesTheRealisticFixtureSkippingDraftsAndOddTags() {
        val list = fixture()
        assertEquals(listOf("v5.0", "v5.0.1", "v4.8.3", "v4.8"), list.map { it.tag })
        assertTrue(list.all { it.prerelease })
        assertEquals("v4.8", list[3].title) // name vacío: se usa la etiqueta
        assertTrue(list[0].notes.contains("Bluetooth completo"))
        assertEquals("https://github.com/DiegoTorresMijarra/MyMouse/releases/tag/v5.0", list[0].pageUrl)
    }

    @Test
    fun picksTheHighestVersionNotTheNewestInTheList() {
        assertEquals("v5.0.1", Releases.highest(fixture())!!.tag)
        assertNull(Releases.highest(emptyList()))
    }

    @Test
    fun findsByVersionNumbers() {
        assertEquals("v5.0", Releases.find(fixture(), v("5.0.0"))!!.tag)
        assertNull(Releases.find(fixture(), v("5.2")))
    }

    @Test
    fun picksTheRightAssetForEachTarget() {
        val release = fixture().first { it.tag == "v5.0" }
        val apk = Releases.pickAsset(release, AssetKind.ANDROID_APK)!!
        assertEquals("MyMouse-5.0.apk", apk.name)
        assertEquals(3145728L + 103, apk.size)
        assertEquals("https://github.com/DiegoTorresMijarra/MyMouse/releases/download/v5.0/MyMouse-5.0.apk", apk.downloadUrl)
        assertEquals(64, apk.sha256!!.length)
        assertEquals("MyMouse-Setup-5.0.exe", Releases.pickAsset(release, AssetKind.WINDOWS_INSTALLER)!!.name)
        val zip = Releases.pickAsset(release, AssetKind.WINDOWS_ZIP)!!
        assertEquals("MyMouse-Windows-5.0.zip", zip.name)
        assertNull(zip.sha256) // sin digest en la API
    }

    @Test
    fun apkPatternDoesNotMatchTheInstallerOrZip() {
        assertFalse(Releases.matches("MyMouse-Setup-5.0.exe", AssetKind.ANDROID_APK))
        assertFalse(Releases.matches("MyMouse-Windows-5.0.zip", AssetKind.ANDROID_APK))
        assertFalse(Releases.matches("MyMouse-5.0.apk", AssetKind.WINDOWS_INSTALLER))
        assertTrue(Releases.matches("MyMouse-0.0-dev.3.apk", AssetKind.ANDROID_APK))
    }

    @Test
    fun releaseWithoutAssetsHasNoAsset() {
        assertNull(Releases.pickAsset(release("v5.1"), AssetKind.ANDROID_APK))
    }

    @Test
    fun errorsAreIllegalArgument() {
        for (json in listOf("""{"message":"API rate limit exceeded"}""", "no es json", "")) {
            assertFailsWith<IllegalArgumentException>(json) { Releases.parse(json) }
        }
    }

    @Test
    fun onlyTrustsGitHubOverHttps() {
        assertTrue(Releases.isTrustedDownloadUrl("https://github.com/DiegoTorresMijarra/MyMouse/releases/download/v5.0/MyMouse-5.0.apk"))
        for (url in listOf("http://github.com/x/y", "https://evil.example.com/MyMouse-5.0.apk", "https://github.com.evil.example/x", "nope", "https://user@github.com/")) {
            assertFalse(Releases.isTrustedDownloadUrl(url), url)
        }
    }

    @Test
    fun userAgentIsGeneric() {
        assertEquals("MyMouse-Updater", Releases.USER_AGENT)
        assertTrue(Releases.LIST_URL.endsWith("/releases?per_page=10"))
    }
}

class UpdatePolicyTest {
    @Test
    fun decidesWhetherToOffer() {
        data class Case(val local: String, val tag: String, val skipped: String?, val expected: Boolean)
        for (c in listOf(
            Case("5.0", "v5.1", null, true),
            Case("5.0", "v5.0", null, false),
            Case("5.1", "v5.0", null, false),
            Case("5.0", "v5.0.1", null, true),
            Case("0.0-dev.12", "v5.1", null, false), // las versiones dev nunca avisan
            Case("0.0", "v5.1", null, false),
            Case("", "v5.1", null, false),
            Case("5.0", "v5.1", "5.1", false), // omitida
            Case("5.0", "v5.2", "5.1", true), // la siguiente sí avisa
            Case("5.0", "v5.1", "5.1.0", false),
            Case("5.0", "v5.1", "basura", true),
        )) {
            assertEquals(c.expected, UpdatePolicy.shouldOffer(AppVersion.parse(c.local), release(c.tag), c.skipped), c.toString())
        }
        assertFalse(UpdatePolicy.shouldOffer(v("5.0"), null, null))
    }

    @Test
    fun phoneSeesNewerPc() {
        val n = UpdatePolicy.crossDevice(VersionSide.PHONE, "5.0", "5.2")
        assertEquals(CrossDeviceKind.OTHER_NEWER, n.kind)
        assertEquals("5.2", n.otherVersion!!.display)
        assertEquals("5.0", n.mineVersion!!.display)
    }

    @Test
    fun phoneSeesOlderPc() {
        val n = UpdatePolicy.crossDevice(VersionSide.PHONE, "5.2", "5.0")
        assertEquals(CrossDeviceKind.OTHER_OLDER, n.kind)
        assertEquals("5.0", n.otherVersion!!.display)
    }

    @Test
    fun pcSideNotices() {
        val newer = UpdatePolicy.crossDevice(VersionSide.PC, "5.0", "5.2")
        assertEquals(CrossDeviceKind.OTHER_NEWER, newer.kind)
        assertEquals("5.2", newer.otherVersion!!.display)
        assertEquals(CrossDeviceKind.OTHER_OLDER, UpdatePolicy.crossDevice(VersionSide.PC, "5.2", "5.0").kind)
    }

    @Test
    fun noCrossNotice() {
        for ((mine, other) in listOf("5.0" to "5.0", "5.0" to "5.0.0", "0.0-dev.3" to "5.2", "5.2" to "0.0-dev.3", "5.0" to "", "5.0" to null, "5.0" to "basura")) {
            assertEquals(CrossDeviceKind.NONE, UpdatePolicy.crossDevice(VersionSide.PHONE, mine, other).kind, "$mine/$other")
        }
    }

    @Test
    fun trimsNotes() {
        assertEquals("a\n\nb", UpdatePolicy.trimNotes("a  \r\n\r\n\r\n\r\nb\r\n"))
        val trimmed = UpdatePolicy.trimNotes(List(200) { "palabra" }.joinToString(" "), 100)
        assertTrue(trimmed.length <= 101)
        assertTrue(trimmed.endsWith("…"))
    }

    @Test
    fun offersTheHighestReleaseWithAnApk() {
        val banners = UpdatePolicy.banners(v("5.0"), "5.0", fixture(), null, null)
        val b = banners.single()
        assertEquals(BannerKind.NEW_VERSION, b.kind)
        assertEquals("5.0.1", b.version.display)
        assertTrue(b.canInstall && b.canSkip)
        assertEquals("v5.0.1", b.release!!.tag)
    }

    @Test
    fun upToDateAndDevHaveNoBanners() {
        assertTrue(UpdatePolicy.banners(v("5.0.1"), "5.0.1", fixture(), null, null).isEmpty())
        assertTrue(UpdatePolicy.banners(v("0.0-dev.99"), "0.0-dev.99", fixture(), null, "5.0").isEmpty())
        assertTrue(UpdatePolicy.banners(null, "rara", fixture(), null, "5.0").isEmpty())
    }

    @Test
    fun skipHidesThatVersionButNotTheNext() {
        assertTrue(UpdatePolicy.banners(v("5.0"), "5.0", fixture(), "5.0.1", null).isEmpty())
        val more = fixture() + release("v5.1")
        assertEquals("v5.1", UpdatePolicy.banners(v("5.0"), "5.0", more, "5.0.1", null).single().release!!.tag)
    }

    @Test
    fun pcNewerShowsInstallBannerWhenNothingOffered() {
        val skipped = "5.0.1"
        val old = UpdatePolicy.banners(v("5.0"), "5.0", fixture(), skipped, "4.8.3").single()
        assertEquals(BannerKind.PC_OLDER, old.kind)
        assertFalse(old.canInstall)

        val newer = UpdatePolicy.banners(v("5.0"), "5.0", fixture(), skipped, "5.0.1").single()
        assertEquals(BannerKind.PC_NEWER, newer.kind)
        assertEquals("v5.0.1", newer.release!!.tag)
        assertTrue(newer.canInstall)
        assertEquals("5.0.1", newer.version.display)
        assertEquals("5.0", newer.mineVersion!!.display)

        val unknown = UpdatePolicy.banners(v("5.0"), "5.0", fixture(), skipped, "7.0").single() // no está en GitHub: solo texto
        assertFalse(unknown.canInstall)
        assertEquals("7.0", unknown.version.display)
    }

    @Test
    fun pcNewerIsCoveredByTheOfferBanner() {
        assertEquals(listOf(BannerKind.NEW_VERSION), UpdatePolicy.banners(v("5.0"), "5.0", fixture(), null, "5.0.1").map { it.kind })
        assertEquals(listOf(BannerKind.NEW_VERSION, BannerKind.PC_OLDER), UpdatePolicy.banners(v("5.0"), "5.0", fixture(), null, "4.8").map { it.kind })
    }

    @Test
    fun withoutNetworkPcNewerHasNoInstallButton() {
        val b = UpdatePolicy.banners(v("5.0"), "5.0", emptyList(), null, "5.2").single()
        assertEquals(BannerKind.PC_NEWER, b.kind)
        assertFalse(b.canInstall)
    }
}
