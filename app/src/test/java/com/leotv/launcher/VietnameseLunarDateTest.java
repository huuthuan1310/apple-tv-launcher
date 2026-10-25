package com.leotv.launcher;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
public class VietnameseLunarDateTest {
    @Test public void tetAcrossYears() {
        assertArrayEquals(new int[]{1,1,2024,0},VietnameseLunarDate.fromSolar(10,2,2024));
        assertArrayEquals(new int[]{1,1,2025,0},VietnameseLunarDate.fromSolar(29,1,2025));
        assertArrayEquals(new int[]{1,1,2026,0},VietnameseLunarDate.fromSolar(17,2,2026));
        assertArrayEquals(new int[]{1,1,2027,0},VietnameseLunarDate.fromSolar(6,2,2027));
    }
    @Test public void leapMonthAndVietnamTimezone() {
        assertArrayEquals(new int[]{1,2,2023,1},VietnameseLunarDate.fromSolar(22,3,2023));
        assertArrayEquals(new int[]{1,1,1985,0},VietnameseLunarDate.fromSolar(21,1,1985));
    }
    @Test public void yearBoundary() {
        assertArrayEquals(new int[]{29,12,2024,0},VietnameseLunarDate.fromSolar(28,1,2025));
    }
}
