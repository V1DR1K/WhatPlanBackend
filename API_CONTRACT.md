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
- Global calendar: `/api/special-dates` stores exact, non-recurring date labels.
  More than one label may use the same date. Reads require authentication;
  creating, updating, and deleting entries require `ADMIN`.
- Global settings: authenticated users can `GET /api/settings`, which returns
  `{ "catalogPageSize": 5 }` by default. `ADMIN` users can `PUT /api/settings`
  with `catalogPageSize` from 1 through 50.

All collections are ordered by their explicit photo position or their relevant
experience date. A visit/cooking cover is selected with `PUT .../cover/{photoId}`.
