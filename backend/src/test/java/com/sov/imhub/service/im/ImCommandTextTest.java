package com.sov.imhub.service.im;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImCommandTextTest {

    @Test
    void sliceFromFirstSlash_skipsLeadingMentionLines() {
        assertEquals("/cx 1 2", ImCommandText.sliceFromFirstSlash("@机器人\n/cx 1 2"));
        assertEquals("/help", ImCommandText.sliceFromFirstSlash("/help"));
        assertEquals("", ImCommandText.sliceFromFirstSlash("no slash"));
    }
}
