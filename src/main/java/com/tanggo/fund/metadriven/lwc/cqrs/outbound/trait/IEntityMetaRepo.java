package com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait;

import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;

import java.util.List;

/**
 * 实体元数据仓储接口
 * 用于管理DClass元数据的持久化和查询
 */
public interface IEntityMetaRepo extends IRepository {
    /**
     * 插入单个实体元数据
     */
    void insert(DClass entity);

    /**
     * 批量插入实体元数据
     */
    void insertBatch(List<DClass> entities);

    /**
     * 查询所有实体元数据
     */
    List<DClass> query();

    /**
     * 根据实体名称查找元数据
     * @param entityName 实体名称
     * @return DClass元数据，如果不存在返回null
     */
    DClass findByName(String entityName);
}
