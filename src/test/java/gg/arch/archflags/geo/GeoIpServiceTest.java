package gg.arch.archflags.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.arch.archflags.api.CountryInfo;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GeoIpServiceTest {

    @Test
    void onlyRealResolutionsAreReusedFromTheCache() {
        assertFalse(GeoIpService.isReusable(null));
        // An Unknown cached while the database was missing must be retried once it loads.
        assertFalse(GeoIpService.isReusable(CountryInfo.UNKNOWN));
        assertTrue(GeoIpService.isReusable(new CountryInfo("SA", "Saudi Arabia", true)));
    }

    @Test
    void recognisesDatabasesThatCarryCountryData() {
        assertTrue(GeoIpService.hasCountryData("GeoLite2-Country"));
        assertTrue(GeoIpService.hasCountryData("GeoLite2-City"));
        assertTrue(GeoIpService.hasCountryData("GeoIP2-Country"));
        assertTrue(GeoIpService.hasCountryData("GeoIP2-Enterprise"));
        assertFalse(GeoIpService.hasCountryData("GeoLite2-ASN"));
        assertFalse(GeoIpService.hasCountryData("GeoIP2-Anonymous-IP"));
        assertFalse(GeoIpService.hasCountryData(null));
    }

    @Test
    void databaseAgeIsWholeDaysAndNeverNegative() {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        assertEquals(45, GeoIpService.ageInDays(now.minus(Duration.ofDays(45)).minusSeconds(60), now));
        assertEquals(0, GeoIpService.ageInDays(now.minusSeconds(3600), now));
        assertEquals(0, GeoIpService.ageInDays(now.plus(Duration.ofDays(2)), now), "clock skew is not an age");
        assertEquals(0, GeoIpService.ageInDays(null, now));
        assertTrue(GeoIpService.ageInDays(now.minus(Duration.ofDays(31)), now) > GeoIpService.STALE_AFTER_DAYS);
    }

    @Test
    void privateAndLocalAddressesAreNeverLookedUp() throws Exception {
        for (String literal : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.1.20",
                "169.254.10.10", "0.0.0.0", "::1", "fe80::1"}) {
            assertTrue(GeoIpService.isNonPublic(InetAddress.getByName(literal)), literal);
        }
        for (String literal : new String[] {"8.8.8.8", "1.1.1.1", "2001:4860:4860::8888"}) {
            assertFalse(GeoIpService.isNonPublic(InetAddress.getByName(literal)), literal);
        }
    }
}
