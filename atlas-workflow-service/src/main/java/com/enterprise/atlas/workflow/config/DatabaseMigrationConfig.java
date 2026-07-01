package com.enterprise.atlas.workflow.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DatabaseMigrationConfig {

    @Bean
    public static BeanFactoryPostProcessor dbMigrationDependsOnPostProcessor() {
        return beanFactory -> {
            String[] entityManagerFactoryNames = beanFactory.getBeanNamesForType(EntityManagerFactory.class, true, false);
            for (String name : entityManagerFactoryNames) {
                BeanDefinition def = beanFactory.getBeanDefinition(name);
                List<String> dependsOn = def.getDependsOn() == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(def.getDependsOn()));
                dependsOn.add("databaseMigrationRunner");
                def.setDependsOn(dependsOn.toArray(new String[0]));
            }
        };
    }
}
