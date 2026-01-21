# Map Type Control Addition and UI Fix

The goal is to fix the map type control (Base/Satellite) which is currently broken and misaligned due to CSS corruption.

## Changes

### 1. Fix `map.css`

- Overwrite `map.css` with clean content to fix encoding and syntax errors.
- Ensure `.map-type-control` is positioned in the bottom-right, above the zoom buttons.
- Fix broken Korean comments and formatting.

### 2. Verify `map.js`

- Ensure layer switching logic is correctly hooked to the buttons.
- Verify VWorld WMTS URLs are correct (Base, Satellite, Hybrid).

### 3. Verify `map.html`

- Ensure button IDs match the JS logic.

## Verification

- Check the map type control is in the bottom-right.
- Check clicking "Satellite" switches to satellite imagery with labels.
- Check clicking "Base" switches back to the standard map.
