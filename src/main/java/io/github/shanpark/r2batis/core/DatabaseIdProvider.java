package io.github.shanpark.r2batis.core;

import io.r2dbc.spi.ConnectionFactory;

import java.util.Map;

@SuppressWarnings("unused")
public interface DatabaseIdProvider {

    void setDatabaseIdMap(Map<String, String> databaseIdMap);
    String getDatabaseId(ConnectionFactory connectionFactory);
}
