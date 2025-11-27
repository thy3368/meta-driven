package com.tanggo.fund.metadriven.lwc.cqrs.outbound;

import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityMetaRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 实体元数据仓储实现
 * 当前使用内存存储，生产环境应改为数据库存储
 */
@Repository
public class EntityMetaRepo implements IEntityMetaRepo {

    // 内存存储（临时实现，生产环境应使用数据库）
    private final Map<String, DClass> entityMetaCache = new ConcurrentHashMap<>();

    @Override
    public void insert(DClass entity) {
        if (entity == null || entity.getName() == null) {
            throw new IllegalArgumentException("Entity or entity name cannot be null");
        }
        entityMetaCache.put(entity.getName(), entity);
    }

    @Override
    public void insertBatch(List<DClass> entities) {
        if (entities == null) {
            return;
        }
        for (DClass entity : entities) {
            insert(entity);
        }
    }

    @Override
    public List<DClass> query() {
        return List.copyOf(entityMetaCache.values());
    }

    @Override
    public DClass findByName(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return null;
        }
        return entityMetaCache.get(entityName);
    }
}
