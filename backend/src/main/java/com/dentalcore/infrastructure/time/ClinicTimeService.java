package com.dentalcore.infrastructure.time;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clinic-local time: financial dates ("today's production") must follow the
 * clinic's timezone, not the server's UTC clock — a 9pm payment in New York
 * belongs to that business day, not tomorrow's.
 */
@Service
public class ClinicTimeService {

    private static final Logger log = LoggerFactory.getLogger(ClinicTimeService.class);
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    private record CachedZone(ZoneId zone, long loadedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<UUID, CachedZone> cache = new ConcurrentHashMap<>();

    public ClinicTimeService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ZoneId clinicZone(UUID clinicId) {
        CachedZone cached = cache.get(clinicId);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt() < CACHE_TTL_MILLIS) {
            return cached.zone();
        }
        ZoneId zone = loadZone(clinicId);
        cache.put(clinicId, new CachedZone(zone, System.currentTimeMillis()));
        return zone;
    }

    public LocalDate today(UUID clinicId) {
        return LocalDate.now(clinicZone(clinicId));
    }

    /** Start of the clinic's current business day, as an instant for timestamp queries. */
    public ZonedDateTime startOfToday(UUID clinicId) {
        return today(clinicId).atStartOfDay(clinicZone(clinicId));
    }

    private ZoneId loadZone(UUID clinicId) {
        try {
            String tz = jdbc.queryForObject(
                    "SELECT timezone FROM clinics WHERE id = :id",
                    Map.of("id", clinicId), String.class);
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            log.warn("Could not resolve timezone for clinic {} — falling back to UTC", clinicId);
            return ZoneId.of("UTC");
        }
    }
}
