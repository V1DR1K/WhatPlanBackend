alter table item_photos add column image_base64 text;
alter table item_photos add column thumbnail_base64 text;
alter table item_photos alter column object_key drop not null;
alter table item_photos alter column thumbnail_key drop not null;
