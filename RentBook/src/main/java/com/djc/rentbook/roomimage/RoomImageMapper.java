package com.djc.rentbook.roomimage;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomImageMapper {
    @Select("""
            select id, room_id, storage_key, url, thumbnail_storage_key, thumbnail_url, original_name, content_type, size_bytes, sort_order, created_at
            from room_images
            where room_id = #{roomId} and deleted = false
            order by sort_order, id
            """)
    List<Map<String, Object>> listByRoom(@Param("roomId") Long roomId);

    @Select("""
            select id, room_id, storage_key, url, thumbnail_storage_key, thumbnail_url, original_name, content_type, size_bytes, sort_order
            from room_images
            where room_id = #{roomId} and deleted = false
            order by sort_order, id
            limit 1
            """)
    RoomImageRecord findActiveByRoom(@Param("roomId") Long roomId);

    @Select("""
            select id, room_id, storage_key, url, thumbnail_storage_key, thumbnail_url, original_name, content_type, size_bytes, sort_order
            from room_images
            where id = #{id} and room_id = #{roomId} and deleted = false
            """)
    RoomImageRecord findActive(@Param("roomId") Long roomId, @Param("id") Long id);

    @Insert("""
            insert into room_images(room_id, storage_key, url, thumbnail_storage_key, thumbnail_url, original_name, content_type, size_bytes, sort_order)
            values(#{roomId}, #{storageKey}, #{url}, #{thumbnailStorageKey}, #{thumbnailUrl}, #{originalName}, #{contentType}, #{sizeBytes}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(RoomImageRecord image);

    @Update("""
            update room_images
            set deleted = true, updated_at = now()
            where room_id = #{roomId} and deleted = false
            """)
    int softDeleteByRoom(@Param("roomId") Long roomId);

    @Update("""
            update room_images
            set deleted = true, updated_at = now()
            where id = #{id} and room_id = #{roomId} and deleted = false
            """)
    int softDelete(@Param("roomId") Long roomId, @Param("id") Long id);
}
