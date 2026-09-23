-- The corresponding objects are uploaded to the existing `ticastr` R2 bucket
-- before this migration is applied. Keep historical/deleted Markdown assets on
-- their original provider; only active assets are served from R2.
UPDATE tb_content_asset
SET provider = 'r2'
WHERE status = 'ACTIVE'
  AND object_key = 'articles/55/3-358b58cf-75f5-4a14-aa71-f76965e861f0.md';

UPDATE tb_media_asset
SET storage_mode = 'r2'
WHERE status = 'ACTIVE'
  AND object_key IN (
    'media/2026/09/4233d536-baf6-475b-bae4-76165fc92f18.jpeg',
    'media/2026/09/9bc179e2-39ac-4f63-9526-607d9e6f7b1d.jpg',
    'media/2026/09/4c543ff4-a808-448f-a418-f16eedfde5eb.jpeg',
    'media/2026/09/6f63e2f5-5ce1-4086-9f8f-43f7bd53d449.png',
    'media/2026/09/4560484e-8efe-42fd-8229-201737f7a035.jpeg',
    'media/2026/09/2242cf55-4408-4624-b65a-2c02ad547e57.jpg'
  );
