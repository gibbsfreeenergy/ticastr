# Reading Orbit Design

**Date:** 2026-08-31  
**Status:** Approved for implementation  
**Scope:** Public Vue blog only

## Goal

Add a playful, discoverable public reading surface that turns the blog's existing article metadata into a visual constellation. The experience should feel like a quiet observatory for the site's ideas while remaining useful: visitors can filter, search, sort, select, and open a real article.

## Product shape

The new route is `/orbit`, displayed in the desktop and mobile public navigation as `阅读星图`. Its visible title is `阅读星图` with the supporting line `把写过的东西，放回同一片天空。`.

The page has three regions:

1. **Observatory header** — a small route label, page title, short description, result count, search input, category filters, and sort controls.
2. **Orbit canvas** — a deep indigo visual field with subtle grid and stars, SVG connector lines, and article nodes positioned deterministically from their index. Each node exposes its article title, category, and date; the selected node receives a clear visual treatment.
3. **Signal detail rail** — a single selected article summary with category, date, tags, and a `打开文章` link. On narrow screens the rail becomes a detail block below the orbit/list.

The canvas is an enhancement, not the only way to access content. At mobile widths the nodes become a readable stacked list with selection preserved.

## Data flow

- `Orbit.vue` requests `this.$api.article.home({ params: { size: 50 }, suppressErrorToast: true, timeout: 5000 })` once on creation. This uses the existing cursor API and never downloads Markdown content.
- The page normalizes missing/null article fields through `orbitState.js` so malformed or partial metadata cannot break rendering.
- The active search query, category, sort mode, selected article, and loading/error state are local component state. Nothing is persisted and no global store mutation is needed.
- If the request fails, the page keeps the shell visible and shows a recoverable `重新连接` action. If the API returns no items, it shows an intentional empty state rather than invented article content.

## Interaction contract

- Search filters by title, category, tags, and short article summary when present.
- Category filters include `全部` plus categories found in the fetched article metadata.
- Sort modes are `最新`, `最早`, and `阅读时间` (estimated from content word count when available, otherwise stable item order).
- `换一个` chooses a different visible article using a deterministic fallback when only one result exists. The selected article can also be changed by clicking or keyboard-focusing a node/list row.
- `打开文章` routes to `/articles/:id` using the existing public article page.
- Escape clears search; Enter on a selected node opens its article. Interactive nodes have accessible labels and visible focus states.

## Visual direction

- Preserve the site's existing typography and Vuetify layout conventions, but introduce a focused dark observatory surface for this route: `#11152b` background, `#171d3b` surfaces, warm `#f6c978` selected accents, cool `#8ca8ff` secondary accents, and white/lavender text.
- Use CSS gradients only for the observatory atmosphere and low-contrast decorative stars; avoid external imagery and new packages.
- Use semantic headings, buttons, labels, and links. Decorative grid/stars are `aria-hidden`.
- Motion is limited to node glow/selection and an ambient drift animation. All motion is disabled under `prefers-reduced-motion: reduce`.
- The existing public site remains unchanged outside the route and the two navigation entry points.

## Files and boundaries

- Create `blog-vue/blog/src/views/orbit/orbitState.js` for pure normalization, filtering, sorting, deterministic positioning, and surprise selection.
- Create `blog-vue/blog/src/views/orbit/orbitState.test.js` for the helper behavior and edge cases.
- Create `blog-vue/blog/src/views/orbit/Orbit.vue` for rendering, request lifecycle, local interaction state, and route navigation.
- Modify `blog-vue/blog/src/router/index.js` to register `/orbit`.
- Modify `blog-vue/blog/src/components/layout/TopNavBar.vue` and `blog-vue/blog/src/components/layout/SideNavBar.vue` to expose `阅读星图`.

## Error handling and compatibility

- Request failures are rendered inline; no new toast or backend behavior is introduced.
- The route works with safe store defaults and with an unavailable API.
- Existing relative `/api` routing, Vue Options API style, double-quoted strings, and directory responsibilities remain intact.

## Verification

- Run the new helper tests first through a red-green cycle, then the complete public test suite, lint, and production build.
- Run the public dev server and inspect `/orbit` at desktop and mobile-sized viewports.
- Verify the core path: load metadata, filter/search, select a node, use `换一个`, and open an article route.
- Check keyboard focus, empty/error states, and reduced-motion CSS in the rendered DOM.

## Non-goals

- No schema, API, authentication, article-content, or admin-console changes.
- No new third-party dependency.
- No fake fallback articles when the API is empty or unavailable.
