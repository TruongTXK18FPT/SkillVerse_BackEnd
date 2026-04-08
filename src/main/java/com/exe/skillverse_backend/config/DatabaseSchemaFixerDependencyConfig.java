package com.exe.skillverse_backend.config;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Ensure runtime schema patches are applied before Hibernate validates the schema.
 */
@Configuration
public class DatabaseSchemaFixerDependencyConfig extends EntityManagerFactoryDependsOnPostProcessor {

    public DatabaseSchemaFixerDependencyConfig() {
        super("databaseSchemaFixer");
    }
}
