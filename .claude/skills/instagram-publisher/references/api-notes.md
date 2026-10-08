# Instagram Graph API — what the skill relies on (verify against current Meta docs when in doubt)

Docs: developers.facebook.com/docs/instagram-platform/content-publishing

## Publishing flow
1. `POST /{ig-user-id}/media` → container (`image_url` | `video_url` + `media_type=REELS|STORIES|VIDEO`, `caption`, …)
2. Poll `GET /{container-id}?fields=status_code` → `IN_PROGRESS` → `FINISHED` (or `ERROR`/`EXPIRED`)
3. `POST /{ig-user-id}/media_publish?creation_id={container-id}`
- Carousel: create each child with `is_carousel_item=true`, then a parent with `media_type=CAROUSEL&children=id1,id2,…`.
- Media must be at a **public, directly fetchable URL** (no redirects to HTML, no auth).

## Constraints the CLI enforces or warns about
| Item | Limit |
|---|---|
| Image format | **JPEG only** (the CLI converts) |
| Feed image ratio | 4:5 (0.8) … 1.91:1; min width 320 px; the CLI outputs 1080 px wide |
| Carousel | 2–10 items; all cropped to the first item's ratio |
| Caption | ≤ 2200 chars, ≤ 30 hashtags, ≤ 20 @mentions |
| Reels | MP4/MOV, H.264 + AAC, 9:16 recommended, ≥ 3 s; very large files may fail |
| Stories | image or video; captions ignored |
| Rate limit | ~100 API-published posts / 24 h (`ig.py quota`) |

## Not possible through the API
Adding trending/licensed audio, product tags, scheduled publishing, filters/stickers, editing a published post's media, posting from personal (non-professional) accounts.

## Permissions
`instagram_basic`, `instagram_content_publish`, `pages_show_list`, `pages_read_engagement` (+ `instagram_manage_comments` for the optional first comment).
Development-mode apps can publish to accounts owned by the app's admins/testers without App Review.

## Insights used by `learn`
`like_count` and `comments_count` per post (may be absent if the account hides likes) → "top performers" = highest likes+comments.
