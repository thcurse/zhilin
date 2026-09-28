package com.zhilin.entity.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 仅测试使用的 MySQL 临时表映射，不作为正式业务实体发布。 */
@Getter
@Setter
@TableName("stage3_connection_check")
public class InfrastructureCheckEntity {

    /** 当前测试连接内的记录标识，由测试指定。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 验收文本，用于检查中文、emoji 及下划线字段到驼峰字段的映射。 */
    private String checkMessage;
}
