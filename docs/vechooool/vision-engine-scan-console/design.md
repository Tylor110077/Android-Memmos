---
version: "alpha"
name: "Vision Engine — Scan Console"
description: "Vision Engine Dashboard Section is designed for demonstrating application workflows and interface hierarchy. Key features include clear information density, modular panels, and interface rhythm. It is suitable for product showcases, admin panels, and analytics experiences."
colors:
  primary: "#10B981"
  secondary: "#EF4444"
  tertiary: "#0A83C9"
  neutral: "#000000"
  background: "#000000"
  surface: "#FFFFFF"
  text-primary: "#FFFFFF"
  text-secondary: "#000000"
  border: "#FFFFFF"
  accent: "#10B981"
typography:
  headline-lg:
    fontFamily: "Roboto"
    fontSize: "30px"
    fontWeight: 300
    lineHeight: "33.6px"
    letterSpacing: "-0.025em"
  body-md:
    fontFamily: "Roboto"
    fontSize: "12px"
    fontWeight: 300
    lineHeight: "19.5px"
  label-md:
    fontFamily: "Roboto"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
rounded:
  full: "9999px"
spacing:
  base: "4px"
  sm: "1px"
  md: "2px"
  lg: "2.4px"
  xl: "3.6px"
  gap: "4px"
  card-padding: "12px"
  section-padding: "88px"
components:
  button-primary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.neutral}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
    padding: "6px"
  button-link:
    textColor: "{colors.surface}"
    rounded: "{rounded.full}"
    padding: "6px"
  card:
    rounded: "23px"
    padding: "20px"
---

## Overview

- **Composition cues:**
  - Layout: Grid
  - Content Width: Bounded
  - Framing: Glassy
  - Grid: Strong

## Colors

The color system uses dark mode with #10B981 as the main accent and #000000 as the neutral foundation.

- **Primary (#10B981):** Main accent and emphasis color.
- **Secondary (#EF4444):** Supporting accent for secondary emphasis.
- **Tertiary (#0A83C9):** Reserved accent for supporting contrast moments.
- **Neutral (#000000):** Neutral foundation for backgrounds, surfaces, and supporting chrome.

- **Usage:** Background: #000000; Surface: #FFFFFF; Text Primary: #FFFFFF; Text Secondary: #000000; Border: #FFFFFF; Accent: #10B981

- **Gradients:** bg-gradient-to-br from-white/30 to-transparent via-white/5, bg-gradient-to-br from-white/25 to-transparent via-white/5, bg-gradient-to-b from-black/50 to-black/80 via-black/10, bg-gradient-to-br from-white/40 to-white/10 via-white/5

## Typography

Typography relies on Roboto across display, body, and utility text.

- **Headlines (`headline-lg`):** Roboto, 30px, weight 300, line-height 33.6px, letter-spacing -0.025em.
- **Body (`body-md`):** Roboto, 12px, weight 300, line-height 19.5px.
- **Labels (`label-md`):** Roboto, 12px, weight 500, line-height 16px.

## Layout

Layout follows a grid composition with reusable spacing tokens. Preserve the grid, bounded structural frame before changing ornament or component styling. Use 4px as the base rhythm and let larger gaps step up from that cadence instead of introducing unrelated spacing values.

Treat the page as a grid / bounded composition, and keep that framing stable when adding or remixing sections.

- **Layout type:** Grid
- **Content width:** Bounded
- **Base unit:** 4px
- **Scale:** 1px, 2px, 2.4px, 3.6px, 4px, 6px, 8px, 12px
- **Section padding:** 88px
- **Card padding:** 12px, 14px, 16px, 20px
- **Gaps:** 4px, 6px, 8px, 12px

## Elevation & Depth

Depth is communicated through glass, border contrast, and reusable shadow or blur treatments. Keep those recipes consistent across hero panels, cards, and controls so the page reads as one material system.

Surfaces should read as glass first, with borders, shadows, and blur only reinforcing that material choice.

- **Surface style:** Glass
- **Borders:** 1px #FFFFFF
- **Shadows:** rgb(255, 255, 255) 0px 0px 0px 0px, rgba(255, 255, 255, 0.1) 0px 0px 0px 1px, rgba(0, 0, 0, 0) 0px 0px 0px 0px; rgba(0, 0, 0, 0) 0px 0px 0px 0px, rgba(0, 0, 0, 0) 0px 0px 0px 0px, rgba(0, 0, 0, 0.1) 0px 10px 15px -3px, rgba(0, 0, 0, 0.1) 0px 4px 6px -4px; rgba(0, 0, 0, 0) 0px 0px 0px 0px, rgba(0, 0, 0, 0) 0px 0px 0px 0px, rgb(255, 255, 255) 0px 0px 0px 12px, rgba(0, 0, 0, 0.15) 0px 30px 60px 0px
- **Blur:** 24px, 40px, 12px

### Techniques
- **Gradient border shell:** Use a thin gradient border shell around the main card. Wrap the surface in an outer shell with 1px padding and a 24px radius. Drive the shell with none so the edge reads like premium depth instead of a flat stroke. Keep the actual stroke understated so the gradient shell remains the hero edge treatment. Inset the real content surface inside the wrapper with a slightly smaller radius so the gradient only appears as a hairline frame.

## Shapes

Shapes rely on a tight radius system anchored by 12px and scaled across cards, buttons, and supporting surfaces. Icon geometry should stay compatible with that soft-to-controlled silhouette.

Use the radius family intentionally: larger surfaces can open up, but controls and badges should stay within the same rounded DNA instead of inventing sharper or pill-only exceptions.

- **Corner radii:** 12px, 16px, 23px, 24px, 48px, 9999px
- **Icon treatment:** Linear
- **Icon sets:** Solar

## Components

Anchor interactions to the detected button styles. Reuse the existing card surface recipe for content blocks.

### Buttons
- **Primary:** background #FFFFFF, text #000000, radius 9999px, padding 6px, border 0px solid rgb(229, 231, 235).
- **Links:** text #FFFFFF, radius 9999px, padding 6px, border 0px solid rgb(229, 231, 235).

### Cards and Surfaces
- **Card surface:** background rgba(0, 0, 0, 0.1), border 0px solid rgb(229, 231, 235), radius 23px, padding 20px, shadow none, blur 40px.

### Iconography
- **Treatment:** Linear.
- **Sets:** Solar.

## Do's and Don'ts

Use these constraints to keep future generations aligned with the current system instead of drifting into adjacent styles.

### Do
- Do use the primary palette as the main accent for emphasis and action states.
- Do keep spacing aligned to the detected 4px rhythm.
- Do reuse the Glass surface treatment consistently across cards and controls.
- Do keep corner radii within the detected 12px, 16px, 23px, 24px, 48px, 9999px family.

### Don't
- Don't introduce extra accent colors outside the core palette roles unless the page needs a new semantic state.
- Don't mix unrelated shadow or blur recipes that break the current depth system.
- Don't exceed the detected moderate motion intensity without a deliberate reason.

## Motion

Motion feels controlled and interface-led across text, layout, and section transitions. Timing clusters around 300ms and 150ms. Easing favors ease and cubic-bezier(0.4. Hover behavior focuses on color and text changes. Scroll choreography uses GSAP ScrollTrigger and Parallax for section reveals and pacing.

**Motion Level:** moderate

**Durations:** 300ms, 150ms

**Easings:** ease, cubic-bezier(0.4, 0, 0.2, 1)

**Hover Patterns:** color, text, transform

**Scroll Patterns:** gsap-scrolltrigger, parallax
