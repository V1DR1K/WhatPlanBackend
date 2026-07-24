# Experience API Contract

Authenticated users collaborate on every mutation; `createdBy` identifies the
creator and `updatedBy` identifies the most recent editor where the resource is mutable.

- Food: `/api/places/{placeId}/visits`, `/api/place-visits/{id}`, and visit-scoped
  `/photos`, `/cover/{photoId}`, and `/reviews`. Media is at
  `/api/place-visit-photos/{id}` and reviews at `/api/place-visit-reviews/{id}`.
- WhyFun: reusable `/api/why-fun/activities` own categories and schedules;
   `/api/why-fun/activities/{id}/visits` creates experiences. Visit media and
   reviews use the corresponding `activity-visits`, `activity-visit-photos`, and
   `activity-visit-reviews` paths. Activity summaries include `visitCount`.
- WhichMovie: film experiences stay under `/api/films/{filmId}/views/{viewId}`;
  view media is `/photos` and `/api/film-view-photos/{id}`.
- WhoCook: reusable `/api/how-cook/recipes` have dated
  `/api/how-cook/recipes/{recipeId}/cookings`; cooking media and reviews are at
  `/api/how-cook/cookings/{id}/photos` and `/reviews`.
- Global calendar: `/api/special-dates` stores date labels with a required
  `recurrence`: `ONCE` applies only to the exact date, `ANNUAL` applies on the
  same month/day every year, and `MONTHLY` applies on the same day each month.
  Requests require `date`, `label`, and `recurrence`; responses include all
  three fields. More than one label may use the same date. Reads require
  authentication; creating, updating, and deleting entries require `ADMIN`.
- Global settings: authenticated users can `GET /api/settings`, which returns
  `{ "catalogPageSize": 5 }` by default. `ADMIN` users can `PUT /api/settings`
  with `catalogPageSize` from 1 through 50.

## Paginated Catalogs

The active film, recipe, and activity catalogs return the shared response
shape `{ "content": [...], "nextCursor": number|null }`. `cursor` is a
zero-based offset into the fully filtered, sorted result. Send `nextCursor` as
the next request's `cursor`; it is `null` when there are no more results.
`size` defaults to `5` and its effective value is clamped to `1..30`.

- `GET /api/films` accepts `cursor`, `size`, `search`, `sort`, plus the
  existing `genre`, `platformId`, and `watched` filters. `search` matches a
  local title or original title. `sort` accepts `rating-desc`, `rating-asc`,
  `date-desc`, and `date-asc` (`rating` and `date` remain aliases for their
  descending variants). Dates use `updatedAt`; the default is `date-desc`.
  Ratings are averages of film reviews. Each content item has
  the existing `FilmDto` fields: `id`, `tmdbId`, `title`, `originalTitle`,
  `synopsis`, `releaseDate`, `posterUrl`, `thumbnailUrl`, `posterWidth`,
  `posterHeight`, `genres`, `platform`, `watchedCount`, `lastWatchedOn`,
  `author`, `reviews`, `views`, `createdAt`, `updatedAt`, and `tmdb`.
- `GET /api/how-cook/recipes` accepts `cursor`, `size`, `search`, `home`,
  `cooked`, and `sort`. `home` is `TOMAS` or `AVRIL` and selects recipes with
  at least one cooking at that home. `cooked=true` selects recipes with cooking
  history; `cooked=false` selects recipes without it. `sort` accepts
  `rating-desc`, `rating-asc`, `date-desc`, and `date-asc` (`rating` and
  `date` are descending aliases); dates use `updatedAt`, and the default is
  `date-desc`. Recipe content fields are `id`, `name`, `sourceUrl`, `photoUrl`,
  `thumbnailUrl`, `photoWidth`, `photoHeight`, `rating`, `cookingCount`,
  `homes`, `ingredients`, `steps`, `createdBy`, `updatedBy`, `createdAt`, and
  `updatedAt`. `rating` is the average of cooking-review ratings or `null` if
  none exists; `homes` is an ordered subset of `["TOMAS", "AVRIL"]`.
- `GET /api/why-fun/activities` accepts `cursor`, `size`, `search`, `visited`,
  `sort`, plus the existing `categoryId` and `subcategoryId` filters. Search
  matches activity name, address, category, or subcategory. `visited=true`
  selects activities with `visitCount > 0`; `visited=false` selects pending
  activities with `visitCount == 0`. `sort` accepts `rating-desc`,
  `rating-asc`, `date-desc`, and `date-asc` (`rating` and `date` are descending
  aliases); dates use `updatedAt`, and the default is `date-desc`. Each
  content item has the existing `ActivityDto` fields: `id`, `name`, `address`,
  `category`, `subcategory`, `schedules`, `profilePhoto`, `rating`,
  `visitCount`, `createdBy`, `updatedBy`, `createdAt`, and `updatedAt`.

Catalog DTO media fields contain only URL and dimension metadata. Image bytes
remain available exclusively from their dedicated photo endpoints.

`GET /api/places` also defaults to `date-desc`, which orders places by
`updatedAt` (then `createdAt`). This makes every active catalog open with the
most recently modified entry first.

All collections are ordered by their explicit photo position or their relevant
experience date. A visit/cooking cover is selected with `PUT .../cover/{photoId}`.
