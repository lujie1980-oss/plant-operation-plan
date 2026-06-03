package com.plantops.masterdata;

import com.plantops.persistence.entity.ProductResourceEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class MasterFieldCatalogStartup {

    private static final Logger log = Logger.getLogger(MasterFieldCatalogStartup.class);

    @Inject
    MasterFieldDefinitionService fieldDefinitionService;

    void onStart(@Observes StartupEvent event) {
        fieldDefinitionService.ensureDefaultsForAllWorkspaces();
        backfillProductResourceExtensions();
    }

    @Transactional
    void backfillProductResourceExtensions() {
        int updated = 0;
        List<ProductResourceEntity> rows = ProductResourceEntity.list("1=1");
        for (ProductResourceEntity row : rows) {
            if (row.extensions != null && !row.extensions.isEmpty()) {
                continue;
            }
            MasterDataExtensionService.backfillProductResourceExtensions(row);
            updated++;
        }
        if (updated > 0) {
            log.infof("Backfilled product_resource.extensions for %d rows", updated);
        }
    }
}
