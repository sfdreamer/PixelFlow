-- Demo seed data. Fixed UUIDs keep restarts idempotent (Flyway applies this once).
-- Cover/result files live in the packaged read-only resource directory: src/main/resources/demo.

INSERT INTO category (id, name, sort_order) VALUES
  ('00000000-0000-4000-8000-000000000001', '宠物写真', 10),
  ('00000000-0000-4000-8000-000000000002', '旅行明信片', 20);

INSERT INTO template (id, category_id, title, description, cover_storage_key, demo_result_key,
                      required_image_count, accepted_mime_types, max_file_size_bytes, active, sort_order, created_at) VALUES
  ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001',
   '柔光宠物肖像',
   '上传一张宠物正面照，模拟生成柔光背景的肖像海报。要求主体清晰、光线均匀，建议使用浅色背景。',
   'demo/cover-pet-soft.png', 'demo/result-pet-soft.png', 1,
   JSON_ARRAY('image/jpeg', 'image/png'), 5242880, TRUE, 10, '2026-11-13 00:00:00.000'),
  ('00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000000001',
   '宠物杂志封面',
   '上传一张宠物照片，模拟生成杂志封面排版。要求毛发细节完整、无遮挡，建议使用纯色背景。',
   'demo/cover-pet-magazine.png', 'demo/result-pet-magazine.png', 1,
   JSON_ARRAY('image/jpeg', 'image/png'), 5242880, TRUE, 20, '2026-11-13 00:00:00.000'),
  ('00000000-0000-4000-8000-000000000201', '00000000-0000-4000-8000-000000000002',
   '海岛旅行明信片',
   '上传两张照片：一张风景、一张人物或物件，模拟合成为海岛主题明信片。',
   'demo/cover-travel-island.png', 'demo/result-travel-island.png', 2,
   JSON_ARRAY('image/jpeg', 'image/png'), 5242880, TRUE, 10, '2026-11-13 00:00:00.000'),
  ('00000000-0000-4000-8000-000000000202', '00000000-0000-4000-8000-000000000002',
   '山野旅行明信片',
   '上传两张照片：一张山野远景、一张旅途细节，模拟合成为山野主题明信片。',
   'demo/cover-travel-mountain.png', 'demo/result-travel-mountain.png', 2,
   JSON_ARRAY('image/jpeg', 'image/png'), 5242880, TRUE, 20, '2026-11-13 00:00:00.000');
