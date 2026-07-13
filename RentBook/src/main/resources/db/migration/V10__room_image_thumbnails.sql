alter table room_images
    add column if not exists thumbnail_storage_key varchar(500),
    add column if not exists thumbnail_url varchar(600);

comment on column room_images.thumbnail_storage_key is '缩略图文件存储相对路径';
comment on column room_images.thumbnail_url is '缩略图访问地址，房间卡片优先使用';
