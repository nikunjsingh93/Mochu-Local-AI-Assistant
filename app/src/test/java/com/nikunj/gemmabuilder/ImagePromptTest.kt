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
        assertNull(imagePromptFrom("crea una página web para mi tienda"))
        assertNull(imagePromptFrom("haz una lista de compras"))
        assertNull(imagePromptFrom("¿qué hay en esta imagen?"))
        assertNull(imagePromptFrom("crea una imagen"))
    }

    @Test
    fun findsSpanishImageRequests() {
        assertEquals("un gato astronauta", imagePromptFrom("Crea una imagen de un gato astronauta"))
        assertEquals("un gato en la luna", imagePromptFrom("¿Puedes generar una imagen de un gato en la luna?"))
        assertEquals("un perro en la playa", imagePromptFrom("Por favor, hazme una foto de un perro en la playa"))
        assertEquals("un dragón azul", imagePromptFrom("Dibújame un dragón azul"))
        assertEquals("un paisaje al atardecer", imagePromptFrom("Quiero que generes una imagen de un paisaje al atardecer"))
        assertEquals("montañas nevadas", imagePromptFrom("genera una imagen de montañas nevadas"))
        assertEquals("una ciudad futurista", imagePromptFrom("Me gustaría una ilustración de una ciudad futurista"))
        assertEquals("un zorro rojo", imagePromptFrom("puedes crearme una imagen de un zorro rojo"))
    }
}
