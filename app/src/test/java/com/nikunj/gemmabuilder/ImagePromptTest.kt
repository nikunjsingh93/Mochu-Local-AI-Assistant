package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagePromptTest {
    @Test
    fun findsImageRequests() {
        assertEquals("cat astronaut", imagePromptFrom("draw a cat astronaut"))
        assertEquals("a beach at sunset", imagePromptFrom("Generate an image of a beach at sunset."))
        assertEquals("a cozy cabin in snow", imagePromptFrom("can you create a picture of a cozy cabin in snow?"))
        assertEquals("red fox", imagePromptFrom("Please paint me a red fox"))
        assertEquals("mountains at dawn", imagePromptFrom("make a wallpaper of mountains at dawn"))
    }

    @Test
    fun leavesOtherRequestsAlone() {
        assertNull(imagePromptFrom("create a website for my bakery"))
        assertNull(imagePromptFrom("make a list of groceries"))
        assertNull(imagePromptFrom("what is in this image?"))
        assertNull(imagePromptFrom("write a thank you note"))
    }
}
