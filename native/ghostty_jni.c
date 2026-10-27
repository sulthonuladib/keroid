// JNI bridge to libghostty-vt: create a terminal, feed it bytes, and hand a
// Compose renderer a flat grid of cells.
//
// Built by scripts/build-ghostty.sh with the NDK, linked against the
// libghostty-vt.so that the same script produces. The VT core is Ghostty's
// (MIT); everything here is Keroid's.

#include <jni.h>
#include <android/keycodes.h>
#include <ghostty/vt.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// Cell flags, mirrored in Kotlin (GhosttyTerminal.kt).
#define KFLAG_BOLD 1
#define KFLAG_ITALIC 2
#define KFLAG_UNDERLINE 4
#define KFLAG_STRIKETHROUGH 8
#define KFLAG_FAINT 16
#define KFLAG_BLINK 32
#define KFLAG_INVERSE 64
#define KFLAG_INVISIBLE 128
#define KFLAG_SELECTED 256

// Ints written per cell to the render array.
#define CELL_STRIDE 5

typedef struct {
  GhosttyTerminal term;
  GhosttyRenderState state;
  GhosttyRenderStateRowIterator rowIter;
  GhosttyRenderStateRowCells cellIter;
  GhosttyKeyEncoder keyEncoder;
  GhosttyKeyEvent keyEvent;
  jint *capture;
  size_t capture_len;
  uint16_t cols;
  uint16_t rows;
} KTerm;

static void kterm_free(KTerm *t) {
  if (!t) return;
  if (t->keyEvent) ghostty_key_event_free(t->keyEvent);
  if (t->keyEncoder) ghostty_key_encoder_free(t->keyEncoder);
  if (t->cellIter) ghostty_render_state_row_cells_free(t->cellIter);
  if (t->rowIter) ghostty_render_state_row_iterator_free(t->rowIter);
  if (t->state) ghostty_render_state_free(t->state);
  if (t->term) ghostty_terminal_free(t->term);
  free(t->capture);
  free(t);
}

JNIEXPORT jlong JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeNew(JNIEnv *env,
                                                            jclass clazz,
                                                            jint cols,
                                                            jint rows) {
  (void)env;
  (void)clazz;
  if (cols <= 0 || rows <= 0 || cols > UINT16_MAX || rows > UINT16_MAX) return 0;
  KTerm *t = calloc(1, sizeof(KTerm));
  if (!t) return 0;
  if (ghostty_terminal_new(NULL, &t->term, (uint16_t)cols, (uint16_t)rows) !=
      GHOSTTY_SUCCESS) {
    kterm_free(t);
    return 0;
  }
  t->cols = (uint16_t)cols;
  t->rows = (uint16_t)rows;
  if (ghostty_render_state_new(NULL, &t->state) != GHOSTTY_SUCCESS ||
      ghostty_render_state_row_iterator_new(NULL, &t->rowIter) != GHOSTTY_SUCCESS ||
      ghostty_render_state_row_cells_new(NULL, &t->cellIter) != GHOSTTY_SUCCESS ||
      ghostty_key_encoder_new(NULL, &t->keyEncoder) != GHOSTTY_SUCCESS ||
      ghostty_key_event_new(NULL, &t->keyEvent) != GHOSTTY_SUCCESS) {
    kterm_free(t);
    return 0;
  }
  return (jlong)(intptr_t)t;
}

JNIEXPORT void JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeFree(JNIEnv *env,
                                                             jclass clazz,
                                                             jlong handle) {
  (void)env;
  (void)clazz;
  kterm_free((KTerm *)(intptr_t)handle);
}

JNIEXPORT jboolean JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeWrite(JNIEnv *env,
                                                              jclass clazz,
                                                              jlong handle,
                                                              jbyteArray bytes) {
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t || !bytes) return JNI_FALSE;
  jsize len = (*env)->GetArrayLength(env, bytes);
  if (len <= 0) return JNI_TRUE;
  // Copy rather than pin: vt_write may call back into allocators, and pinning
  // for an unbounded call risks stalling the GC.
  uint8_t *buf = malloc((size_t)len);
  if (!buf) return JNI_FALSE;
  (*env)->GetByteArrayRegion(env, bytes, 0, len, (jbyte *)buf);
  ghostty_terminal_vt_write(t->term, (const uint8_t *)buf, (size_t)len);
  free(buf);
  return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeResize(JNIEnv *env,
                                                               jclass clazz,
                                                               jlong handle,
                                                               jint cols,
                                                               jint rows,
                                                               jint cell_width,
                                                               jint cell_height) {
  (void)env;
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t || cols <= 0 || rows <= 0 || cols > UINT16_MAX || rows > UINT16_MAX) return;
  // Cell pixels are a hint for programs that ask; 0 means unknown.
  ghostty_terminal_resize(t->term, (uint16_t)cols, (uint16_t)rows,
                          (uint32_t)(cell_width > 0 ? cell_width : 0),
                          (uint32_t)(cell_height > 0 ? cell_height : 0));
  t->cols = (uint16_t)cols;
  t->rows = (uint16_t)rows;
}

JNIEXPORT void JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeScroll(JNIEnv *env,
    jclass clazz, jlong handle, jint delta, jboolean bottom) {
  (void)env; (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t) return;
  GhosttyTerminalScrollViewport scroll = {
    .tag = bottom ? GHOSTTY_SCROLL_VIEWPORT_BOTTOM : GHOSTTY_SCROLL_VIEWPORT_DELTA,
    .value = {.delta = delta},
  };
  ghostty_terminal_scroll_viewport(t->term, scroll);
}

JNIEXPORT void JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeSelect(JNIEnv *env,
    jclass clazz, jlong handle, jint x, jint y, jboolean start) {
  (void)env; (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t) return;
  if (x < 0 || y < 0) {
    ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_SELECTION, NULL);
    return;
  }
  GhosttyPoint point = {.tag = GHOSTTY_POINT_TAG_VIEWPORT,
    .value = {.coordinate = {.x = (uint16_t)x, .y = (uint32_t)y}}};
  GhosttyGridRef ref;
  if (ghostty_terminal_grid_ref(t->term, point, &ref) != GHOSTTY_SUCCESS) return;
  GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
  if (start || ghostty_terminal_get(t->term, GHOSTTY_TERMINAL_DATA_SELECTION, &selection) != GHOSTTY_SUCCESS) {
    GhosttyTerminalSelectWordOptions options = GHOSTTY_INIT_SIZED(GhosttyTerminalSelectWordOptions);
    options.ref = ref;
    if (ghostty_terminal_select_word(t->term, &options, &selection) != GHOSTTY_SUCCESS) {
      selection.start = ref; selection.end = ref;
    }
  } else {
    selection.end = ref;
  }
  // Copies immediately into terminal-owned tracked references. No borrowed
  // grid references survive this call or any subsequent output/resize.
  ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_SELECTION, &selection);
}

JNIEXPORT jbyteArray JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeCopy(JNIEnv *env,
    jclass clazz, jlong handle) {
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t) return NULL;
  GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
  if (ghostty_terminal_get(t->term, GHOSTTY_TERMINAL_DATA_SELECTION, &selection) != GHOSTTY_SUCCESS) return NULL;
  GhosttyFormatterTerminalOptions options = GHOSTTY_INIT_SIZED(GhosttyFormatterTerminalOptions);
  options.emit = GHOSTTY_FORMATTER_FORMAT_PLAIN;
  options.unwrap = true; options.trim = true; options.selection = &selection;
  GhosttyFormatter formatter = NULL;
  if (ghostty_formatter_terminal_new(NULL, &formatter, t->term, options) != GHOSTTY_SUCCESS) return NULL;
  uint8_t *buf = NULL;
  size_t len = 0;
  jbyteArray result = NULL;
  if (ghostty_formatter_format_alloc(formatter, NULL, &buf, &len) == GHOSTTY_SUCCESS && len <= INT32_MAX) {
    result = (*env)->NewByteArray(env, (jsize)len);
    if (result && len) (*env)->SetByteArrayRegion(env, result, 0, (jsize)len, (jbyte *)buf);
  }
  if (buf) ghostty_free(NULL, buf, len);
  ghostty_formatter_free(formatter);
  return result;
}

static GhosttyKey android_key(jint code) {
  if (code >= AKEYCODE_A && code <= AKEYCODE_Z) return GHOSTTY_KEY_A + (code - AKEYCODE_A);
  if (code >= AKEYCODE_0 && code <= AKEYCODE_9) return GHOSTTY_KEY_DIGIT_0 + (code - AKEYCODE_0);
  if (code >= AKEYCODE_F1 && code <= AKEYCODE_F12) return GHOSTTY_KEY_F1 + (code - AKEYCODE_F1);
  switch (code) {
    case AKEYCODE_ESCAPE: return GHOSTTY_KEY_ESCAPE;
    case AKEYCODE_TAB: return GHOSTTY_KEY_TAB;
    case AKEYCODE_ENTER: return GHOSTTY_KEY_ENTER;
    case AKEYCODE_NUMPAD_ENTER: return GHOSTTY_KEY_NUMPAD_ENTER;
    case AKEYCODE_DEL: return GHOSTTY_KEY_BACKSPACE;
    case AKEYCODE_FORWARD_DEL: return GHOSTTY_KEY_DELETE;
    case AKEYCODE_MOVE_HOME: return GHOSTTY_KEY_HOME;
    case AKEYCODE_MOVE_END: return GHOSTTY_KEY_END;
    case AKEYCODE_PAGE_UP: return GHOSTTY_KEY_PAGE_UP;
    case AKEYCODE_PAGE_DOWN: return GHOSTTY_KEY_PAGE_DOWN;
    case AKEYCODE_INSERT: return GHOSTTY_KEY_INSERT;
    case AKEYCODE_DPAD_UP: return GHOSTTY_KEY_ARROW_UP;
    case AKEYCODE_DPAD_DOWN: return GHOSTTY_KEY_ARROW_DOWN;
    case AKEYCODE_DPAD_LEFT: return GHOSTTY_KEY_ARROW_LEFT;
    case AKEYCODE_DPAD_RIGHT: return GHOSTTY_KEY_ARROW_RIGHT;
    case AKEYCODE_SPACE: return GHOSTTY_KEY_SPACE;
    case AKEYCODE_COMMA: return GHOSTTY_KEY_COMMA;
    case AKEYCODE_PERIOD: return GHOSTTY_KEY_PERIOD;
    case AKEYCODE_GRAVE: return GHOSTTY_KEY_BACKQUOTE;
    case AKEYCODE_MINUS: return GHOSTTY_KEY_MINUS;
    case AKEYCODE_EQUALS: return GHOSTTY_KEY_EQUAL;
    case AKEYCODE_LEFT_BRACKET: return GHOSTTY_KEY_BRACKET_LEFT;
    case AKEYCODE_RIGHT_BRACKET: return GHOSTTY_KEY_BRACKET_RIGHT;
    case AKEYCODE_BACKSLASH: return GHOSTTY_KEY_BACKSLASH;
    case AKEYCODE_SEMICOLON: return GHOSTTY_KEY_SEMICOLON;
    case AKEYCODE_APOSTROPHE: return GHOSTTY_KEY_QUOTE;
    case AKEYCODE_SLASH: return GHOSTTY_KEY_SLASH;
    default: return GHOSTTY_KEY_UNIDENTIFIED;
  }
}

JNIEXPORT jbyteArray JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeKey(JNIEnv *env,
    jclass clazz, jlong handle, jint code, jint mods, jbyteArray text, jint unshifted, jint action) {
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t) return NULL;
  ghostty_key_encoder_setopt_from_terminal(t->keyEncoder, t->term);
  ghostty_key_event_set_key(t->keyEvent, android_key(code));
  ghostty_key_event_set_mods(t->keyEvent, (GhosttyMods)mods);
  ghostty_key_event_set_consumed_mods(t->keyEvent, 0);
  ghostty_key_event_set_action(t->keyEvent, (GhosttyKeyAction)action);
  ghostty_key_event_set_unshifted_codepoint(t->keyEvent, (uint32_t)unshifted);
  jsize len = text ? (*env)->GetArrayLength(env, text) : 0;
  char *utf8 = len ? malloc((size_t)len) : NULL;
  if (len && !utf8) return NULL;
  if (len) (*env)->GetByteArrayRegion(env, text, 0, len, (jbyte *)utf8);
  ghostty_key_event_set_utf8(t->keyEvent, utf8, (size_t)len);
  size_t size = 0;
  GhosttyResult status = ghostty_key_encoder_encode(t->keyEncoder, t->keyEvent, NULL, 0, &size);
  jbyteArray result = NULL;
  if ((status == GHOSTTY_SUCCESS || status == GHOSTTY_OUT_OF_SPACE) && size <= INT32_MAX) {
    char *buf = size ? malloc(size) : NULL;
    if (!size || buf) {
      if (ghostty_key_encoder_encode(t->keyEncoder, t->keyEvent, buf, size, &size) == GHOSTTY_SUCCESS) {
        result = (*env)->NewByteArray(env, (jsize)size);
        if (result && size) (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (jbyte *)buf);
      }
      free(buf);
    }
  }
  ghostty_key_event_set_utf8(t->keyEvent, NULL, 0);
  free(utf8);
  return result;
}

static jint rgb(GhosttyColorRgb c) {
  return (jint)(0xFF000000u | ((uint32_t)c.r << 16) | ((uint32_t)c.g << 8) |
                (uint32_t)c.b);
}

static GhosttyColorRgb argb(jint value) {
  GhosttyColorRgb c = {(uint8_t)((value >> 16) & 0xFF), (uint8_t)((value >> 8) & 0xFF),
                       (uint8_t)(value & 0xFF)};
  return c;
}

// nativeSetTheme applies a Ghostty theme's chrome colours and its 16 program
// colours over the built-in 256-colour palette. Programs may still override
// their own colours (OSC 4/10/11/12), exactly as on the desktop.
JNIEXPORT jboolean JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeSetTheme(JNIEnv *env,
    jclass clazz, jlong handle, jint fg, jint bg, jint cursor, jintArray palette16) {
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t || !t->term) return JNI_FALSE;
  GhosttyColorRgb fg_rgb = argb(fg), bg_rgb = argb(bg), cursor_rgb = argb(cursor);
  bool ok = ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_COLOR_FOREGROUND, &fg_rgb) == GHOSTTY_SUCCESS &&
            ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_COLOR_BACKGROUND, &bg_rgb) == GHOSTTY_SUCCESS &&
            ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_COLOR_CURSOR, &cursor_rgb) == GHOSTTY_SUCCESS;
  jsize count = palette16 ? (*env)->GetArrayLength(env, palette16) : 0;
  if (count >= 16) {
    jint values[16];
    (*env)->GetIntArrayRegion(env, palette16, 0, 16, values);
    GhosttyColorRgb palette[256];
    ghostty_color_palette_default(palette);
    for (int i = 0; i < 16; i++) palette[i] = argb(values[i]);
    ok = ok && ghostty_terminal_set(t->term, GHOSTTY_TERMINAL_OPT_COLOR_PALETTE, palette) == GHOSTTY_SUCCESS;
  }
  return ok ? JNI_TRUE : JNI_FALSE;
}

// nativeRender fills out (cells * CELL_STRIDE ints) and cursor (4 ints) and
// returns the number of cells written: codepoint, fg, bg, flags, width.
JNIEXPORT jint JNICALL
Java_com_sulthonuladib_keroid_term_GhosttyTerminal_nativeRender(JNIEnv *env,
                                                               jclass clazz,
                                                               jlong handle,
                                                               jintArray out,
                                                               jintArray cursor) {
  (void)clazz;
  KTerm *t = (KTerm *)(intptr_t)handle;
  if (!t || !out) return 0;
  if (ghostty_render_state_update(t->state, t->term) != GHOSTTY_SUCCESS) return 0;

  jsize out_len = (*env)->GetArrayLength(env, out);
  jsize capacity = out_len / CELL_STRIDE;
  // Never hold a JNI critical/pinned array across VT calls and allocations.
  // Reuse native scratch space, then copy the finished snapshot back to Java.
  if ((size_t)out_len > t->capture_len) {
    jint *grown = realloc(t->capture, sizeof(jint) * (size_t)out_len);
    if (!grown) return 0;
    t->capture = grown; t->capture_len = (size_t)out_len;
  }
  jint *cells = t->capture;
  if (!cells) return 0;
  memset(cells, 0, sizeof(jint) * (size_t)out_len);

  if (cursor && (*env)->GetArrayLength(env, cursor) >= 4) {
    jint cur[4] = {0};
    {
      GhosttyRenderStateCursor c = GHOSTTY_INIT_SIZED(GhosttyRenderStateCursor);
      ghostty_render_state_get(t->state, GHOSTTY_RENDER_STATE_DATA_CURSOR, &c);
      cur[0] = c.viewport_has_value ? (jint)c.viewport_x : 0;
      cur[1] = c.viewport_has_value ? (jint)c.viewport_y : 0;
      cur[2] = c.visible && c.viewport_has_value ? 1 : 0;
      GhosttyTerminalScrollbar bar = {0};
      ghostty_terminal_get(t->term, GHOSTTY_TERMINAL_DATA_SCROLLBAR, &bar);
      cur[3] = (jint)(bar.total - bar.len - bar.offset);
      (*env)->SetIntArrayRegion(env, cursor, 0, 4, cur);
    }
  }

  GhosttyRenderStateColors colors = GHOSTTY_INIT_SIZED(GhosttyRenderStateColors);
  ghostty_render_state_get(t->state, GHOSTTY_RENDER_STATE_DATA_COLORS, &colors);
  jint default_fg = rgb(colors.foreground);
  jint default_bg = rgb(colors.background);

  ghostty_render_state_get(t->state, GHOSTTY_RENDER_STATE_DATA_ROW_ITERATOR,
                           &t->rowIter);
  jint n = 0;
  int y = 0;
  while (n < capacity && ghostty_render_state_row_iterator_next(t->rowIter)) {
    ghostty_render_state_row_get(t->rowIter, GHOSTTY_RENDER_STATE_ROW_DATA_CELLS,
                                 &t->cellIter);
    int x = 0;
    while (n < capacity && ghostty_render_state_row_cells_next(t->cellIter)) {
      jint *cell = cells + (size_t)n * CELL_STRIDE;
      uint32_t cp_len = 0;
      ghostty_render_state_row_cells_get(
          t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_LEN, &cp_len);
      if (cp_len > 0) {
        // BUF requires room for the full grapheme, not a truncated count.
        uint32_t stack[16];
        uint32_t *cps = cp_len <= 16 ? stack : malloc((size_t)cp_len * sizeof(uint32_t));
        if (cps) {
          ghostty_render_state_row_cells_get(
              t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_BUF, cps);
          cell[0] = (jint)cps[0];
          if (cps != stack) free(cps);
        }
      } else {
        cell[0] = ' ';
      }
      GhosttyColorRgb fg = {0}, bg = {0};
      if (ghostty_render_state_row_cells_get(
              t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_FG_COLOR, &fg) ==
          GHOSTTY_SUCCESS) {
        cell[1] = rgb(fg);
      } else {
        cell[1] = default_fg;
      }
      if (ghostty_render_state_row_cells_get(
              t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_BG_COLOR, &bg) ==
          GHOSTTY_SUCCESS) {
        cell[2] = rgb(bg);
      } else {
        cell[2] = default_bg;
      }
      bool styled = false;
      ghostty_render_state_row_cells_get(
          t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_HAS_STYLING, &styled);
      jint flags = 0;
      if (styled) {
        GhosttyStyle s = GHOSTTY_INIT_SIZED(GhosttyStyle);
        if (ghostty_render_state_row_cells_get(
                t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_STYLE, &s) ==
            GHOSTTY_SUCCESS) {
          if (s.bold) flags |= KFLAG_BOLD;
          if (s.italic) flags |= KFLAG_ITALIC;
          if (s.faint) flags |= KFLAG_FAINT;
          if (s.blink) flags |= KFLAG_BLINK;
          if (s.inverse) flags |= KFLAG_INVERSE;
          if (s.invisible) flags |= KFLAG_INVISIBLE;
          if (s.strikethrough) flags |= KFLAG_STRIKETHROUGH;
          if (s.underline != 0) flags |= KFLAG_UNDERLINE;
        }
      }
      bool selected = false;
      ghostty_render_state_row_cells_get(t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_SELECTED, &selected);
      if (selected) flags |= KFLAG_SELECTED;
      cell[3] = flags;
      GhosttyCell raw = 0;
      GhosttyCellWide wide = GHOSTTY_CELL_WIDE_NARROW;
      ghostty_render_state_row_cells_get(t->cellIter, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_RAW, &raw);
      ghostty_cell_get(raw, GHOSTTY_CELL_DATA_WIDE, &wide);
      cell[4] = wide == GHOSTTY_CELL_WIDE_WIDE ? 2 : wide == GHOSTTY_CELL_WIDE_SPACER_TAIL ? 0 : 1;
      n++;
      x++;
    }
    y++;
  }

  (*env)->SetIntArrayRegion(env, out, 0, out_len, cells);
  return n;
}
