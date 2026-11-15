package io.github.dwicki.tidytudo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AddressTest {

    @Test
    fun `user input is normalised to a base URL`() {
        assertEquals("http://192.168.1.20", RobotAddress.normalize(" 192.168.1.20 "))
        assertEquals("http://robot.local:8080", RobotAddress.normalize("robot.local:8080/"))
        assertEquals("https://robot.lan", RobotAddress.normalize("https://robot.lan/some/path"))
        assertEquals("https://robot.lan:8443", RobotAddress.normalize("https://robot.lan:8443"))
        assertEquals("http://[fe80::1]", RobotAddress.normalize("fe80::1"))
        assertEquals("http://[fd00::5]:81", RobotAddress.normalize("http://[fd00::5]:81"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(RobotAddress.normalize(""))
        assertNull(RobotAddress.normalize("two words"))
        assertNull(RobotAddress.normalize("ftp://robot"))
    }

    @Test
    fun `private ranges are recognised`() {
        listOf("10.0.0.1", "172.16.5.4", "192.168.178.2", "127.0.0.1", "169.254.1.1", "fd12::1", "fe80::1", "100.100.1.1")
            .forEach { assertTrue(it, PrivateAddress.isPrivate(InetAddress.getByName(it))) }
        listOf("8.8.8.8", "1.1.1.1", "2001:4860::8888", "172.32.0.1")
            .forEach { assertFalse(it, PrivateAddress.isPrivate(InetAddress.getByName(it))) }
    }

    @Test
    fun `redactor removes addresses and credentials`() {
        val text = Redactor.redact(
            "GET http://192.168.1.20/api/v2 failed, Authorization: Basic dXNlcjpwdw== password=hunter2 host robot.local ip 10.0.0.7",
        )
        listOf("192.168", "dXNlcjpwdw", "hunter2", "robot.local", "10.0.0.7").forEach { secret ->
            assertFalse("$secret leaked in: $text", text.contains(secret))
        }
    }
}
