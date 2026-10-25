package com.leotv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GooglePhotosValidationTest {

    @Test
    public void testDomainValidation() {
        assertTrue(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://photos.app.goo.gl/exampleAlbum123"));
        assertTrue(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("http://photos.app.goo.gl/test"));
        assertTrue(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("photos.app.goo.gl/quicklink"));
        assertTrue(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://photos.google.com/share/AF1QipN..."));
        assertTrue(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://subdomain.photos.google.com/share"));

        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain(null));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain(""));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("   "));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://google.com"));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://drive.google.com/drive/folders/123"));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("https://facebook.com/album"));
        assertFalse(GooglePhotosWallpaperLoader.isValidGooglePhotosDomain("not a valid url"));
    }

    @Test
    public void testNormalizeUrl() {
        assertEquals("https://photos.app.goo.gl/test", GooglePhotosWallpaperLoader.normalizeUrl("photos.app.goo.gl/test"));
        assertEquals("https://photos.app.goo.gl/test", GooglePhotosWallpaperLoader.normalizeUrl("https://photos.app.goo.gl/test"));
        assertEquals("http://photos.app.goo.gl/test", GooglePhotosWallpaperLoader.normalizeUrl("http://photos.app.goo.gl/test"));
        assertEquals("", GooglePhotosWallpaperLoader.normalizeUrl(null));
        assertEquals("", GooglePhotosWallpaperLoader.normalizeUrl("   "));
    }

    @Test
    public void testSlideshowTransitions() {
        assertEquals(SlideshowTransition.FADE, SlideshowTransition.fromKey("FADE"));
        assertEquals(SlideshowTransition.KEN_BURNS, SlideshowTransition.fromKey("KEN_BURNS"));
        assertEquals(SlideshowTransition.SLIDE_HORIZONTAL, SlideshowTransition.fromKey("SLIDE_HORIZONTAL"));
        assertEquals(SlideshowTransition.ZOOM_FADE, SlideshowTransition.fromKey("ZOOM_FADE"));
        assertEquals(SlideshowTransition.FADE, SlideshowTransition.fromKey("invalid_key"));
        assertEquals(SlideshowTransition.FADE, SlideshowTransition.fromKey(null));
    }
}
