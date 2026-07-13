package com.djc.rentbook.tenant;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface TenantMapper {
    @Select("""
            select t.*, coalesce(nullif(p.address, ''), p.name) as property_name, r.room_no, c.end_date as contract_end_date
            from tenants t
            left join contracts c on c.tenant_id = t.id and c.status = 'ACTIVE'
            left join rooms r on r.id = c.room_id
            left join properties p on p.id = r.property_id
            where t.deleted = false
              and (#{keyword,jdbcType=VARCHAR} is null
                   or t.name ilike concat('%', #{keyword,jdbcType=VARCHAR}, '%')
                   or t.phone ilike concat('%', #{keyword,jdbcType=VARCHAR}, '%'))
            order by t.created_at desc
            """)
    List<Map<String, Object>> list(@Param("keyword") String keyword);

    @Select("select count(*) from contracts where tenant_id = #{tenantId} and deleted = false and status = 'ACTIVE'")
    int countActiveContracts(@Param("tenantId") Long tenantId);

    @Insert("""
            insert into tenants(name, phone, id_card, emergency_contact, emergency_phone, source, notes)
            values(#{name}, #{phone}, #{idCard}, #{emergencyContact}, #{emergencyPhone}, #{source}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(TenantRecord tenant);

    @Update("""
            update tenants
            set name = #{name},
                phone = #{phone},
                id_card = #{idCard},
                emergency_contact = #{emergencyContact},
                emergency_phone = #{emergencyPhone},
                source = #{source},
                notes = #{notes},
                updated_at = now()
            where id = #{id} and deleted = false
            """)
    int update(TenantRecord tenant);

    @Update("update tenants set deleted = true, updated_at = now() where id = #{id} and deleted = false")
    int delete(@Param("id") Long id);
}
