package com.airmaxfield.help

import org.junit.Assert.*
import org.junit.Test

class HelpContentTest {

    @Test
    fun field_lookup_exact() {
        val help = HelpContent.forKey("system.hostname")
        assertNotNull(help)
        assertEquals("Имя хоста", help?.title)
    }

    @Test
    fun field_lookup_wildcard() {
        val help = HelpContent.forKey("wireless.1.psk")
        assertNotNull(help)
        assertEquals("Ключ WPA", help?.title)
        assertTrue(help?.key?.contains("*") == true)
    }

    @Test
    fun field_lookup_wildcard_radio_freq() {
        val help1 = HelpContent.forKey("radio.1.freq")
        val help2 = HelpContent.forKey("radio.2.freq")
        assertNotNull(help1)
        assertNotNull(help2)
        assertEquals("Частота", help1?.title)
        assertEquals(help1?.title, help2?.title)
    }

    @Test
    fun search_finds_articles() {
        val results = HelpContent.search("антенн")
        assertTrue(results.isNotEmpty())

        val article = results.firstOrNull { it is HelpArticle } as? HelpArticle
        assertNotNull(article)
        assertTrue(article?.title?.contains("антенн", ignoreCase = true) == true ||
                   article?.body?.contains("антенн", ignoreCase = true) == true)
    }

    @Test
    fun search_finds_fields() {
        val results = HelpContent.search("частот")
        assertTrue(results.isNotEmpty())

        val field = results.firstOrNull { it is FieldHelp } as? FieldHelp
        assertNotNull(field)
        assertTrue(field?.title?.contains("частот", ignoreCase = true) == true ||
                   field?.description?.contains("частот", ignoreCase = true) == true)
    }

    @Test
    fun search_case_insensitive() {
        val results1 = HelpContent.search("сигнал")
        val results2 = HelpContent.search("СИГНАЛ")
        val results3 = HelpContent.search("СиГнАл")

        assertTrue(results1.isNotEmpty())
        assertEquals(results1.size, results2.size)
        assertEquals(results1.size, results3.size)
    }

    @Test
    fun search_returns_empty_on_blank() {
        assertTrue(HelpContent.search("").isEmpty())
        assertTrue(HelpContent.search("   ").isEmpty())
    }

    @Test
    fun search_by_tag() {
        val results = HelpContent.search("диагностика")
        assertTrue(results.isNotEmpty())

        val article = results.filterIsInstance<HelpArticle>()
            .firstOrNull { it.tags.contains("диагностика") }
        assertNotNull(article)
    }

    @Test
    fun all_sections_covered() {
        val wirelessFields = HelpContent.fields.filter {
            it.key.startsWith("radio.") || it.key.startsWith("wireless.")
        }
        val networkFields = HelpContent.fields.filter {
            it.key.startsWith("netconf.") || it.key.startsWith("bridge.") ||
            it.key.startsWith("vlan.") || it.key.startsWith("resolv.")
        }
        val advancedFields = HelpContent.fields.filter {
            it.key.contains("distance") || it.key.contains("ack") ||
            it.key.startsWith("airmax.")
        }
        val servicesFields = HelpContent.fields.filter {
            it.key.startsWith("snmp.") || it.key.startsWith("sshd.") ||
            it.key.startsWith("httpd.") || it.key.startsWith("ntpclient.") ||
            it.key.startsWith("syslog.") || it.key.startsWith("discovery.")
        }
        val systemFields = HelpContent.fields.filter {
            it.key.startsWith("users.") || it.key.startsWith("system.") ||
            it.key.startsWith("update.")
        }

        assertTrue("Wireless section should have at least 3 fields", wirelessFields.size >= 3)
        assertTrue("Network section should have at least 3 fields", networkFields.size >= 3)
        assertTrue("Advanced section should have at least 3 fields", advancedFields.size >= 3)
        assertTrue("Services section should have at least 3 fields", servicesFields.size >= 3)
        assertTrue("System section should have at least 3 fields", systemFields.size >= 3)
    }

    @Test
    fun articles_have_required_fields() {
        assertTrue("Should have at least 5 articles", HelpContent.articles.size >= 5)

        HelpContent.articles.forEach { article ->
            assertFalse("Article ID should not be blank", article.id.isBlank())
            assertFalse("Article title should not be blank", article.title.isBlank())
            assertFalse("Article body should not be blank", article.body.isBlank())
            assertTrue("Article should have at least one tag", article.tags.isNotEmpty())
        }
    }

    @Test
    fun forKey_returns_null_for_unknown() {
        val help = HelpContent.forKey("unknown.key.that.does.not.exist")
        assertNull(help)
    }

    @Test
    fun stage4_and_5_help_present() {
        for (k in listOf("aaa.1.wpa.psk", "wpasupplicant.profile.1.network.1.psk", "radio.countrycode", "radio.1.mode",
            "wireless.1.wds.status", "route.1.gateway", "snmp.location")) {
            assertNotNull(k, HelpContent.forKey(k))
        }
        val ids = HelpContent.articles.map { it.id }
        assertTrue(ids.containsAll(listOf("safe-write", "bridge-wizard", "both-ends", "restore-backup")))
        assertTrue(HelpContent.search("тест-режим").isNotEmpty())
    }
}
