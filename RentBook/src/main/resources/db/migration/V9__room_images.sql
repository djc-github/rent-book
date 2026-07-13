create table if not exists room_images (
    id bigserial primary key,
    room_id bigint not null references rooms(id),
    storage_key varchar(500) not null,
    url varchar(600) not null,
    original_name varchar(255),
    content_type varchar(100),
    size_bytes bigint,
    sort_order int not null default 1,
    deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uk_room_images_one_active
    on room_images(room_id)
    where deleted = false;

create index if not exists idx_room_images_room_active
    on room_images(room_id, sort_order, id)
    where deleted = false;

comment on table room_images is '房间图片表，记录房间主图及文件存储信息';
comment on column room_images.id is '房间图片ID';
comment on column room_images.room_id is '所属房间ID';
comment on column room_images.storage_key is '文件存储相对路径';
comment on column room_images.url is '图片访问地址';
comment on column room_images.original_name is '上传时原始文件名';
comment on column room_images.content_type is '文件MIME类型';
comment on column room_images.size_bytes is '文件大小，单位字节';
comment on column room_images.sort_order is '排序号，当前最多1张时固定为1';
comment on column room_images.deleted is '软删除标记，true表示已删除';
comment on column room_images.created_at is '创建时间';
comment on column room_images.updated_at is '更新时间';
