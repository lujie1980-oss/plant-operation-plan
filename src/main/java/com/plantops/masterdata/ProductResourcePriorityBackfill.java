package com.plantops.masterdata;

import com.plantops.config.LegacySchemaSupport;
import com.plantops.persistence.entity.ProductResourceEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

/** 启动时将 product_resource.resource_priority 空值回填为默认值 1。 */
@ApplicationScoped
public class ProductResourcePriorityBackfill {

    private static final Logger log = Logger.getLogger(ProductResourcePriorityBackfill.class);

    @Inject
    Instance<ProductResourcePriorityBackfill> self;

    @Inject
    LegacySchemaSupport legacySchemaSupport;

    void onStart(@Observes StartupEvent event) {
        if (!legacySchemaSupport.isLegacySchemaEnabled()) {
            return;
        }
        try {
            int updated = self.get().backfillNullPriorities();
            if (updated > 0) {
                log.infof("product_resource resource_priority 已回填默认值 %d：%d 行",
                        ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY, updated);
            }
        } catch (Exception e) {
            log.warnf(e, "product_resource resource_priority 回填跳过");
        }
    }

    @Transactional
    int backfillNullPriorities() {
        List<ProductResourceEntity> rows = ProductResourceEntity.list("resourcePriority is null");
        for (ProductResourceEntity row : rows) {
            row.resourcePriority = ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY;
        }
        return rows.size();
    }
}
