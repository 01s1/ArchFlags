package gg.arch.archflags.api;

/**
 * Immutable, resolved country result for a player. Never carries a raw IP address -- by the time
 * this object exists, the address has already been discarded.
 */
public final class CountryInfo {

    public static final CountryInfo UNKNOWN = new CountryInfo("UN", "Unknown", false);

    private final String isoCode;
    private final String name;
    private final boolean resolved;

    public CountryInfo(String isoCode, String name, boolean resolved) {
        this.isoCode = isoCode;
        this.name = name;
        this.resolved = resolved;
    }

    /** Upper-case ISO 3166-1 alpha-2 code, e.g. "SA". */
    public String isoCode() {
        return isoCode;
    }

    /** English display name, e.g. "Saudi Arabia". */
    public String name() {
        return name;
    }

    /** False if the address could not be geolocated (private/local IP, DB miss, DB missing). */
    public boolean isResolved() {
        return resolved;
    }
}
