package com.kapi.notes.domain.model

/** Background pattern drawn behind the strokes of a blank page. */
enum class PaperType {
    BLANK,
    LINED,
    GRID,
    DOT,
    /** Narrow cue column on the left, ruled notes area, summary strip at the bottom. */
    CORNELL,
    /** Repeating checkbox + ruled-line rows, for to-do style notes. */
    CHECKLIST,
    /** A weekly grid (days as columns, time slots as rows). */
    TIMETABLE,
    /** 田字格 (tian zi ge) - large squares, each split into quarters by a dashed cross, used for
     * practicing Chinese/Japanese character stroke proportions. */
    CHINESE_CHARACTER_GRID,
    /** 原稿用紙 (genkou youshi) - plain square grid (no quarter-cross) in a fixed column x row
     * layout, the standard sheet for writing Japanese prose/essays by hand. */
    GENKOU_YOUSHI,
    /** Repeating 5-line staff, for writing music notation. */
    MUSIC_STAFF,
    /** A plain ruled table (fixed rows x columns), for structured notes/comparisons. */
    TABLE,
    /** Isometric dot grid (two families of dots on 30deg axes), for 3D/technical sketching. */
    ISOMETRIC_DOT,
    /** 4-line ruled guide (baseline/midline/topline/descender), for handwriting practice. */
    HANDWRITING_GUIDE
}

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

/** Horizontal mirror of the page content, toggled by the "Lật" (flip) action. */
enum class PageFlip {
    NONE,
    HORIZONTAL
}
