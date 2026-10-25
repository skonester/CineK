package me.xdan.aperture.data.repository

import me.xdan.aperture.data.remote.dto.TmdbResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataYearTest {
    private fun movie(date: String?) = TmdbResult(1, "Manhunter", null, null, null, null, date, null, null)

    @Test
    fun fallbackAcceptsOnlyTitlesWithinAYear() {
        // A festival year against the release year still matches.
        assertTrue(isNearYear(2017, movie("2018-03-02")))
        assertTrue(isNearYear(2017, movie("2016-09-10")))
        // A same-named film from another decade doesn't.
        assertFalse(isNearYear(2017, movie("1986-08-15")))
        assertFalse(isNearYear(2017, movie(null)))
        assertTrue(isNearYear(2017, TmdbResult(2, null, "Show", null, null, null, null, "2017-01-01", null)))
    }
}
