package com.quyetbkhoa.phonebridge.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun `finds APK and checksum assets`() {
        val release = GitHubReleaseParser.parse(
            """{
              "tag_name":"v0.10.0",
              "name":"PhoneBridge v0.10.0",
              "html_url":"https://github.com/quyetbkhoa/PhoneBridge/releases/tag/v0.10.0",
              "assets":[
                {"name":"PhoneBridge-v0.10.0.apk","browser_download_url":"https://github.com/quyetbkhoa/PhoneBridge/releases/download/v0.10.0/PhoneBridge-v0.10.0.apk","size":42},
                {"name":"PhoneBridge-v0.10.0.apk.sha256","browser_download_url":"https://github.com/quyetbkhoa/PhoneBridge/releases/download/v0.10.0/PhoneBridge-v0.10.0.apk.sha256","size":64}
              ]
            }"""
        )

        assertEquals("v0.10.0", release.tagName)
        assertNotNull(release.version)
        assertEquals("PhoneBridge-v0.10.0.apk", release.apk?.name)
        assertEquals("PhoneBridge-v0.10.0.apk.sha256", release.checksum?.name)
    }
}
