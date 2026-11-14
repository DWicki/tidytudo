package io.github.dwicki.tidytudo.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class SubnetScannerTest {

    @Test
    fun `a slash 24 yields 253 other hosts`() {
        val own = InetAddress.getByName("192.168.1.42") as Inet4Address
        val hosts = SubnetScanner.hostsInSubnet(own, 24)
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first().hostAddress)
        assertEquals("192.168.1.254", hosts.last().hostAddress)
        assertFalse(hosts.contains(own))
    }

    @Test
    fun `a slash 30 yields one other host`() {
        val own = InetAddress.getByName("10.0.0.1") as Inet4Address
        assertEquals(listOf("10.0.0.2"), SubnetScanner.hostsInSubnet(own, 30).map { it.hostAddress })
    }
}
