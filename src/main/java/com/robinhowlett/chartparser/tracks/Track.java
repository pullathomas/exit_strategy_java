package com.robinhowlett.chartparser.tracks;

/**
 * Stores a track's code, canonical code (for continuity), country, and name
 */
public class Track {

    private String code;
    // the original code if a track has changed code or uses multiple codes for sponsorship reasons
    // e.g. PHA for PRX, HOL for BHP and OTH, SA for OSA etc.
    private String canonical;
    private String country;
    private String state;
    private String city;
    private String name;

    public String getCode() {
        return code.trim();
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCanonical() {
        return (canonical != null && !canonical.trim().isEmpty() ? canonical.trim() : getCode());
    }

    public void setCanonical(String canonical) {
        this.canonical = canonical;
    }

    public String getState() {
        return (state != null && !state.trim().isEmpty()) ? state.trim() : null;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return (country != null && !country.trim().isEmpty()) ? country.trim() : null;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return (city != null && !city.trim().isEmpty()) ? city.trim() : null;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Track{" +
                "code='" + code + '\'' +
                ", canonical='" + canonical + '\'' +
                ", country='" + country + '\'' +
                ", state='" + state + '\'' +
                ", city='" + city + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Track track = (Track) o;

        if (code != null ? !code.equals(track.code) : track.code != null) return false;
        if (canonical != null ? !canonical.equals(track.canonical) : track.canonical != null)
            return false;
        if (country != null ? !country.equals(track.country) : track.country != null) return false;
        if (state != null ? !state.equals(track.state) : track.state != null) return false;
        if (city != null ? !city.equals(track.city) : track.city != null) return false;
        return name != null ? name.equals(track.name) : track.name == null;
    }

    @Override
    public int hashCode() {
        int result = code != null ? code.hashCode() : 0;
        result = 31 * result + (canonical != null ? canonical.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (city != null ? city.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
