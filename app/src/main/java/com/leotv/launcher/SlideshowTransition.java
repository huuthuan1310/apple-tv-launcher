package com.leotv.launcher;

/**
 * Transition effects for wallpaper slideshow in Apple TV launcher.
 */
public enum SlideshowTransition {
    FADE(R.string.leo_transition_fade, R.string.leo_transition_fade_desc),
    KEN_BURNS(R.string.leo_transition_ken, R.string.leo_transition_ken_desc),
    SLIDE_HORIZONTAL(R.string.leo_transition_slide, R.string.leo_transition_slide_desc),
    ZOOM_FADE(R.string.leo_transition_zoom, R.string.leo_transition_zoom_desc);

    public final int title;
    public final int description;

    SlideshowTransition(int title, int description) {
        this.title = title;
        this.description = description;
    }

    public static SlideshowTransition fromKey(String key) {
        if (key == null) return FADE;
        for (SlideshowTransition t : values()) {
            if (t.name().equalsIgnoreCase(key)) return t;
        }
        return FADE;
    }
}
