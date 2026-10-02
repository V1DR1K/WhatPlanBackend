ALTER TABLE couples
  ADD COLUMN media_used_bytes bigint NOT NULL DEFAULT 0,
  ADD COLUMN media_photo_count integer NOT NULL DEFAULT 0,
  ADD COLUMN media_quota_bytes bigint NOT NULL DEFAULT 536870912,
  ADD COLUMN media_quota_photos integer NOT NULL DEFAULT 2000;

ALTER TABLE couples
  ADD CONSTRAINT chk_couples_media_counters CHECK (media_used_bytes >= 0 AND media_photo_count >= 0),
  ADD CONSTRAINT chk_couples_media_quotas CHECK (media_quota_bytes > 0 AND media_quota_photos > 0),
  ADD CONSTRAINT chk_couples_media_quota CHECK (
    media_used_bytes <= media_quota_bytes AND media_photo_count <= media_quota_photos
  );

-- Account for the existing shared-schema media before enabling enforcement.
-- Existing couples are grandfathered by raising their limit to their current usage.
WITH photo_sizes AS (
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint AS size_bytes
    FROM item_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM place_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM place_visit_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM film_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM recipe_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM home_recipe_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM why_fun_venue_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM why_fun_visit_photos
  UNION ALL
  SELECT couple_id, octet_length(coalesce(image_base64, ''))::bigint
      + octet_length(coalesce(thumbnail_base64, ''))::bigint FROM special_date_occurrence_photos
), media_totals AS (
  SELECT couple_id, sum(size_bytes)::bigint AS used_bytes, count(*)::integer AS photo_count
    FROM photo_sizes
   GROUP BY couple_id
)
UPDATE couples AS couple
   SET media_used_bytes = coalesce(media_totals.used_bytes, 0),
       media_photo_count = coalesce(media_totals.photo_count, 0),
       media_quota_bytes = greatest(couple.media_quota_bytes, coalesce(media_totals.used_bytes, 0)),
       media_quota_photos = greatest(couple.media_quota_photos, coalesce(media_totals.photo_count, 0))
  FROM (SELECT id FROM couples) AS all_couples
  LEFT JOIN media_totals ON media_totals.couple_id = all_couples.id
 WHERE couple.id = all_couples.id;

CREATE FUNCTION enforce_couple_media_quota() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  target_couple uuid;
  byte_delta bigint;
  photo_delta integer;
BEGIN
  IF TG_OP = 'INSERT' THEN
    target_couple := NEW.couple_id;
    byte_delta := octet_length(coalesce(NEW.image_base64, ''))::bigint
        + octet_length(coalesce(NEW.thumbnail_base64, ''))::bigint;
    photo_delta := 1;
  ELSIF TG_OP = 'DELETE' THEN
    target_couple := OLD.couple_id;
    byte_delta := -(octet_length(coalesce(OLD.image_base64, ''))::bigint
        + octet_length(coalesce(OLD.thumbnail_base64, ''))::bigint);
    photo_delta := -1;
  ELSE
    IF NEW.couple_id IS DISTINCT FROM OLD.couple_id THEN
      RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'chk_media_couple_immutable',
        MESSAGE = 'media_couple_immutable';
    END IF;
    target_couple := NEW.couple_id;
    byte_delta := octet_length(coalesce(NEW.image_base64, ''))::bigint
        + octet_length(coalesce(NEW.thumbnail_base64, ''))::bigint
        - octet_length(coalesce(OLD.image_base64, ''))::bigint
        - octet_length(coalesce(OLD.thumbnail_base64, ''))::bigint;
    photo_delta := 0;
  END IF;

  UPDATE couples
     SET media_used_bytes = greatest(0, media_used_bytes + byte_delta),
         media_photo_count = greatest(0, media_photo_count + photo_delta)
   WHERE id = target_couple
     AND (TG_OP = 'DELETE' OR (
       media_used_bytes + byte_delta <= media_quota_bytes
       AND media_photo_count + photo_delta <= media_quota_photos
     ));

  IF NOT FOUND THEN
    -- Parent deletion cascades may delete media after the couple row itself is gone.
    IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM couples WHERE id = target_couple) THEN
      RETURN OLD;
    END IF;
    RAISE EXCEPTION USING
      ERRCODE = '23514',
      CONSTRAINT = 'chk_couples_media_quota',
      MESSAGE = 'media_quota_exceeded';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_item_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON item_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_place_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON place_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_place_visit_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON place_visit_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_film_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON film_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_recipe_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON recipe_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_home_recipe_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON home_recipe_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_why_fun_venue_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON why_fun_venue_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_why_fun_visit_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON why_fun_visit_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
CREATE TRIGGER trg_special_date_occurrence_photos_couple_media_quota
  AFTER INSERT OR UPDATE OF couple_id, image_base64, thumbnail_base64 OR DELETE ON special_date_occurrence_photos
  FOR EACH ROW EXECUTE FUNCTION enforce_couple_media_quota();
