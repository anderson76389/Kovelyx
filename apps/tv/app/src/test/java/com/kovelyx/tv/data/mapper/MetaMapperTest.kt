package com.kovelyx.tv.data.mapper

import com.kovelyx.tv.data.remote.dto.MetaResponseDto
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaMapperTest {

    private val adapter = Moshi.Builder().build().adapter(MetaResponseDto::class.java)

    @Test
    fun `localized addon certification takes priority`() {
        val result = parse(
            """
            {
              "meta": {
                "id": "show",
                "type": "series",
                "name": "Show",
                "app_extras": {
                  "certificationLocal": " 16 ",
                  "certification": "TV-MA"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("16", result.ageRating)
    }

    @Test
    fun `default addon certification is used when localized value is blank`() {
        val result = parse(
            """
            {
              "meta": {
                "id": "movie",
                "type": "movie",
                "name": "Movie",
                "app_extras": {
                  "certificationLocal": " ",
                  "certification": " PG-13 "
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("PG-13", result.ageRating)
    }

    @Test
    fun `episode IMDb and generic scores survive JSON strings and numbers without relabelling`() {
        val result = parse("""
            {"meta":{"id":"tt1234567","type":"series","name":"Show","videos":[
                {"id":"tt1234567:1:1","season":1,"episode":1,"rating":"7.2","imdbRating":8.7},
                {"id":"tt1234567:1:2","season":1,"episode":2,"rating":7.5},
                {"id":"tt1234567:1:3","season":1,"episode":3,"imdbRating":"11"}
            ]}}
        """.trimIndent())
        assertEquals(8.7, result.videos[0].imdbRating!!, 0.0)
        assertEquals(7.2, result.videos[0].rating!!, 0.0)
        assertEquals(7.5, result.videos[1].rating!!, 0.0)
        assertEquals(null, result.videos[1].imdbRating)
        assertEquals(null, result.videos[2].imdbRating)
    }

    private fun parse(json: String) = requireNotNull(adapter.fromJson(json)?.meta).toDomain()
}
