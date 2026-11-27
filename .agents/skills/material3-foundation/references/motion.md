# Motion

## The model

Material motion has three layers, and you should reach for the top one first.

1. **Physics** — springs with damping and stiffness.
2. **Semantics** — `MotionScheme` names a spec for a job: fast/default/slow ×
   spatial/effects.
3. **Patterns** — container transform, shared axis, fade through, fade.

A component or screen asks for a *job*, not a duration. The scheme answers with
a spring tuned to the theme.

```
screen → motionScheme.defaultSpatialSpec() → spring(damping, stiffness)
```

## Spatial vs effects

| Kind | Animates | Use |
| --- | --- | --- |
| Spatial | position, size, bounds, shape, scale, rotation | Anything that moves through space |
| Effects | color, alpha, elevation | Anything that changes appearance in place |

Spatial springs may overshoot. Effects springs are critically damped
(`dampingRatio = 1.0`) because a bouncing color reads as a glitch.

## Spring tokens

These are the exact values behind `MotionScheme.standard()` and
`MotionScheme.expressive()` in `androidx.compose.material3.tokens`.

| Scheme | Speed | Spatial damping / stiffness | Effects damping / stiffness |
| --- | --- | --- | --- |
| Standard | fast | 0.9 / 1400 | 1.0 / 3800 |
| Standard | default | 0.9 / 700 | 1.0 / 1600 |
| Standard | slow | 0.9 / 300 | 1.0 / 800 |
| Expressive | fast | 0.6 / 800 | 1.0 / 3800 |
| Expressive | default | 0.8 / 380 | 1.0 / 1600 |
| Expressive | slow | 0.8 / 200 | 1.0 / 800 |

Reading the table:

- Lower damping → visible overshoot. Expressive fast (0.6) is the bounciest.
- Higher stiffness → shorter, snappier settle. Fast effects (3800) are near
  instant.
- Standard spatial never overshoots much (0.9); it is the utilitarian feel.
- Expressive is slower and springier at default and slow: it is for hero and
  prominent elements, not for every row.

## Choosing a spec

| Situation | Spec |
| --- | --- |
| Small element, frequent or reversible (checkbox, ripple, chip) | `fast*` |
| Most transitions, standard screen content | `default*` |
| Large surface, hero, onboarding, dramatic reveal | `slow*` |
| Movement, resize, shape change | `*SpatialSpec()` |
| Color, alpha, fade, elevation | `*EffectsSpec()` |

```kotlin
val scheme = MaterialTheme.motionScheme

val offset by animateDpAsState(
    targetValue = if (expanded) 0.dp else 24.dp,
    animationSpec = scheme.defaultSpatialSpec(),
    label = "offset",
)

val containerColor by animateColorAsState(
    targetValue = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    },
    animationSpec = scheme.fastEffectsSpec(),
    label = "containerColor",
)
```

`animate*AsState` infers the type parameter. If you need it explicitly, use
`scheme.defaultSpatialSpec<Dp>()`.

## Patterns

| Pattern | Use | Compose |
| --- | --- | --- |
| Container transform | A list item opens into a detail screen; a FAB becomes a sheet | `SharedTransitionLayout` + `Modifier.sharedElement` / `sharedBounds` |
| Shared axis | Forward/back between peers, onboarding steps | `AnimatedContent` with `slideInHorizontally`/`slideOutHorizontally` + fade |
| Fade through | Switching between unrelated destinations | `fadeIn` + `scaleIn(initialScale = 0.92f)` crossing with `fadeOut` |
| Fade | A small element appears or disappears in place | `AnimatedVisibility` + `fadeIn`/`fadeOut` |
| Expand / collapse | Revealing details, accordions | `AnimatedVisibility` + `expandVertically`/`shrinkVertically` |
| Content resize | Text or a container changes size | `Modifier.animateContentSize(...)` |

```kotlin
AnimatedContent(
    targetState = screen,
    transitionSpec = {
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        (slideInHorizontally(animationSpec = spatial) { it / 4 } +
            fadeIn(animationSpec = effects)) togetherWith
            (slideOutHorizontally(animationSpec = spatial) { -it / 4 } +
                fadeOut(animationSpec = effects))
    },
    label = "screen",
) { current ->
    Screen(current)
}
```

Rules for pattern work:

- One pattern per navigation relationship. Mixing a shared element with a
  full-screen slide makes both unreadable.
- Enter and exit are one gesture: keep directions and durations consistent so
  back feels like the inverse of forward.
- Animate the smallest subtree that changes. Hoisting an animation to the screen
  root turns a local change into a whole-page repaint.

## Tween fallback: easing and duration tokens

Springs are the default. Use `tween` only when physics cannot express the
change: progress indicators, shimmer, `drawBehind` animation, or a canvas loop.
Then use the M3 tokens instead of an arbitrary number.

| Duration token | ms |
| --- | --- |
| short1–4 | 50, 100, 150, 200 |
| medium1–4 | 250, 300, 350, 400 |
| long1–4 | 450, 500, 550, 600 |
| extraLong1–4 | 700, 800, 900, 1000 |

| Easing token | Cubic bezier |
| --- | --- |
| linear | 0, 0, 1, 1 |
| standard | 0.2, 0, 0, 1 |
| standard decelerate | 0, 0, 0, 1 |
| standard accelerate | 0.3, 0, 1, 1 |
| emphasized | 0.2, 0, 0, 1 |
| emphasized decelerate | 0.05, 0.7, 0.1, 1 |
| emphasized accelerate | 0.3, 0, 0.8, 0.15 |

```kotlin
tween(
    durationMillis = 300, // medium2
    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f), // emphasized decelerate
)
```

Entering content decelerates; leaving content accelerates. Linear is for
indeterminate progress and continuous loops, not for UI transitions.

## Interruption and gestures

- Use `Animatable`, `animate*AsState`, or `updateTransition`; they retarget from
  the current value and velocity. A chain of `tween`s cannot be interrupted
  cleanly.
- Drag gestures should hand off velocity: `Animatable.animateTo(target,
  initialVelocity = velocity)`.
- For dismissal, fling with `splineBasedDecay` and settle with a spatial spec.
- Never block input while an animation runs; the target state should be
  reachable at any frame.

## Performance

- Animate `Modifier.graphicsLayer` (alpha, scale, translation) for transforms;
  it skips layout and draw remeasurement.
- `Modifier.offset { IntOffset(...) }` defers to the layout phase; prefer it
  over `offset(x.dp, y.dp)`, which runs composition.
- Avoid animating `width`/`height` on complex subtrees; scale or clip instead.
- `Modifier.animateItem()` in lazy lists is the correct tool for reordering;
  give it a spec from the scheme when the default feels wrong.

## Accessibility

- Compose animations honor the system animator duration scale. When the user
  disables animations, standard Compose APIs jump to the end state.
- Custom `withFrameNanos` loops do not. Gate them on
  `ValueAnimator.areAnimatorsEnabled()`.
- Never encode meaning only in motion. A moving element needs a static cue too.
- Keep essential transitions under ~400ms of perceived delay; expressive slow
  springs are for non-blocking decoration.

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| `tween(300)` scattered in features | `MaterialTheme.motionScheme.*Spec()` |
| `LinearEasing` on a screen transition | Spatial/effects spec, or emphasized easing |
| Animating color with a spatial spring | `*EffectsSpec()` |
| Animating position with an effects spec | `*SpatialSpec()` |
| `spring(dampingRatio = 0.3f)` inline | The scheme, or a theme-level override |
| Parent and child both animating the same change | Animate the child only |
| Blocking the UI until an animation finishes | Retargetable state-driven animation |
| Infinite animation for decoration | A static alternative, or honor reduced motion |

## Verify

- Interrupt every animation mid-flight (tap again, navigate back); no snap.
- Set the system animator scale to 0; the UI still reaches the correct state.
- Watch on a 60Hz and a 120Hz device; springs should settle, not crawl.
- Profile with Macrobenchmark or Layout Inspector if frames drop.
