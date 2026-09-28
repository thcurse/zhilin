package com.zhilin.mapper.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhilin.entity.infrastructure.InfrastructureCheckEntity;
import org.apache.ibatis.annotations.Param;

/** 仅测试使用的 Mapper，同时验证 MyBatis-Plus 基础 CRUD 和自定义 XML 加载。 */
public interface InfrastructureCheckMapper extends BaseMapper<InfrastructureCheckEntity> {

    /**
     * 通过 XML 查询当前连接的临时表，验证资源路径与字段映射。
     *
     * @param id 临时记录标识
     * @return 找到的验收记录，不存在时为 null
     */
    InfrastructureCheckEntity selectCheckById(@Param("id") Long id);
}
