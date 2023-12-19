package io.github.shanpark.r2batis.core;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@SuppressWarnings("unused")
@Slf4j
public class VendorDatabaseIdProvider implements DatabaseIdProvider {
    private Map<String, String> databaseIdMap;

    @Override
    public String getDatabaseId(ConnectionFactory connectionFactory) {
        if (connectionFactory == null)
            throw new NullPointerException("connectionFactory cannot be null");

        try {
            return getDatabaseName(connectionFactory);
        } catch (Exception e) {
            log.error("Could not get a databaseId from connectionFactory", e);
        }
        return null;
    }

    @Override
    public void setDatabaseIdMap(Map<String, String> databaseIdMap) {
        this.databaseIdMap = databaseIdMap;
    }

    private String getDatabaseName(ConnectionFactory connectionFactory) {
        String productName = connectionFactory.getMetadata().getName();
        if (databaseIdMap != null) {
            for (Map.Entry<String, String> entry : databaseIdMap.entrySet()) {
                if (productName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            // no match, return null
            return null;
        }
        return productName;
    }
}
