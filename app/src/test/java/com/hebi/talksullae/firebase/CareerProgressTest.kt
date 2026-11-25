package com.hebi.talksullae.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class CareerProgressTest {
    @Test fun levelsAndTitlesHaveStableBoundaries(){
        assertEquals(CareerProgress(0,1,"신입 탐정"),careerProgress(0))
        assertEquals(1L,careerProgress(9).level)
        assertEquals(CareerProgress(100,2,"초급 탐정"),careerProgress(10))
        assertEquals("숙련 탐정",careerProgress(50).title)
        assertEquals("추리 달인",careerProgress(100).title)
        assertEquals(0L,careerProgress(-1).xp)
    }
}
