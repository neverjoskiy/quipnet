package com.example.quipnet

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Дефект, ради которого написаны эти проверки: туннель шёл через Proton
 * (`tun0 = 10.2.0.2`, выход NL), служба публиковала `transport=AWG PROTON`, а на
 * бейдже стояло «WARP: NL».
 *
 * Причина — регистр. `ClientData.getServiceTransport()` приводит прочитанное к
 * верхнему регистру, а `TRANSPORT_AWG_PROTON` — единственная константа со
 * строчными буквами, поэтому прямое `==` было ложным всегда. У `MASQUE`, `AWG`,
 * `WARP` и `OPERA` буквы и так заглавные, и мимо проходил ровно Proton: одна
 * ветка из пяти, которую глазами не отличить от рабочих.
 */
class PublishedTransportTest {

    /** Ровно то, что делает `ClientData.getServiceTransport()` с прочитанным. */
    private fun published(value: String) = value.trim().uppercase(Locale.US)

    @Test
    fun `метка транспорта переживает приведение к верхнему регистру`() {
        listOf(
            QuipNetVpnService.TRANSPORT_MASQUE,
            QuipNetVpnService.TRANSPORT_VLESS,
            QuipNetVpnService.TRANSPORT_WARP,
            QuipNetVpnService.TRANSPORT_AWG,
            QuipNetVpnService.TRANSPORT_AWG_PROTON,
            QuipNetVpnService.TRANSPORT_OPERA,
        ).forEach { transport ->
            assertTrue(
                "метка $transport не узнаётся после uppercase — бейдж покажет чужой транспорт",
                QuipNetVpnService.isPublishedTransport(published(transport), transport)
            )
        }
    }

    @Test
    fun `именно Proton ломался при прямом сравнении`() {
        // Пояснение к тесту выше: без учёта регистра ложным было только это равенство.
        assertFalse(published(QuipNetVpnService.TRANSPORT_AWG_PROTON) == QuipNetVpnService.TRANSPORT_AWG_PROTON)
        assertTrue(published(QuipNetVpnService.TRANSPORT_MASQUE) == QuipNetVpnService.TRANSPORT_MASQUE)
    }

    @Test
    fun `чужая метка не выдаёт себя за нашу`() {
        assertFalse(QuipNetVpnService.isPublishedTransport("MASQUE", QuipNetVpnService.TRANSPORT_AWG_PROTON))
        assertFalse(QuipNetVpnService.isPublishedTransport("AWG", QuipNetVpnService.TRANSPORT_AWG_PROTON))
        assertFalse(QuipNetVpnService.isPublishedTransport("", QuipNetVpnService.TRANSPORT_AWG_PROTON))
        assertFalse(QuipNetVpnService.isPublishedTransport(null, QuipNetVpnService.TRANSPORT_AWG_PROTON))
        // Пробелы по краям файл состояния переживал и раньше.
        assertTrue(QuipNetVpnService.isPublishedTransport("  awg proton  ", QuipNetVpnService.TRANSPORT_AWG_PROTON))
    }
}
