package com.warframemarket.collector.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiParsingTest {

    private final ObjectMapper mapper =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void parsesItemsPayloadAndIgnoresUnknownFields() throws Exception {
        String json = """
                {
                  "apiVersion": "0.25.0",
                  "data": [
                    {
                      "id": "54a73e65e779893a797fff1b",
                      "slug": "ash_prime_set",
                      "gameRef": "/Lotus/Whatever",
                      "tags": ["set", "prime"],
                      "i18n": { "en": { "name": "Ash Prime Set", "thumb": "thumb.png" } },
                      "somethingNew": 42
                    }
                  ],
                  "error": null
                }""";

        ApiDtos.ItemsResponse response = mapper.readValue(json, ApiDtos.ItemsResponse.class);

        assertEquals(1, response.data().size());
        ApiDtos.ApiItem item = response.data().get(0);
        assertEquals("ash_prime_set", item.slug());
        assertEquals("Ash Prime Set", item.displayName());
        assertEquals(java.util.List.of("set", "prime"), item.tags());
    }

    @Test
    void fallsBackToSlugWhenNoLocalisedNameExists() throws Exception {
        ApiDtos.ApiItem item =
                mapper.readValue("""
                        {"id":"1","slug":"vitality","tags":["mod"]}""", ApiDtos.ApiItem.class);
        assertEquals("vitality", item.displayName());
        assertNull(item.i18n());
    }

    @Test
    void parsesTopOrdersPayload() throws Exception {
        String json = """
                {
                  "apiVersion": "0.25.0",
                  "data": {
                    "sell": [
                      {"id":"a","type":"sell","platinum":78,"quantity":1,"visible":true,
                       "user":{"ingameName":"someone","status":"ingame"}}
                    ],
                    "buy": [
                      {"id":"b","type":"buy","platinum":70,"quantity":1,"visible":true}
                    ]
                  }
                }""";

        ApiDtos.TopOrdersResponse response = mapper.readValue(json, ApiDtos.TopOrdersResponse.class);

        assertEquals(1, response.data().sell().size());
        assertEquals(78, response.data().sell().get(0).platinum());
        assertEquals(70, response.data().buy().get(0).platinum());
    }
}
