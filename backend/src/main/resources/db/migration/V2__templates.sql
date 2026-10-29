INSERT INTO template_categories VALUES ('creative','创意图片',1),('portrait','人像写真',2),('brand','品牌设计',3);
INSERT INTO templates(id,category_id,title,description,cover_url,required_image_count) VALUES
('postcard','creative','山野来信','把旅途的两张照片，收进一封来自山野的明信片。','/api/demo-files/postcard.svg',2),
('pet','creative','毛茸茸的主角','让你的毛茸伙伴成为杂志封面的主角。','/api/demo-files/pet.svg',1),
('portrait','portrait','午后光影','一张照片，一段温柔的午后时光。','/api/demo-files/portrait.svg',1),
('duo','portrait','我们的同框','上传两张照片，收藏属于你们的共同记忆。','/api/demo-files/duo.svg',2),
('product','brand','好物陈列室','给日常好物一个干净而有质感的舞台。','/api/demo-files/product.svg',1),
('moodboard','brand','灵感拼贴','三张素材，拼出品牌的第一份灵感。','/api/demo-files/moodboard.svg',3);
