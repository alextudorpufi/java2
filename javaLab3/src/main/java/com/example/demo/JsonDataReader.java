package com.example.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Profile("prod")
@ConditionalOnExpression("'${app.data.format}' == 'json'")
public class JsonDataReader implements DataReader {

    private final DataProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public JsonDataReader(
            DataProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<User> readData() throws Exception {

        Resource resource =
                resourceLoader.getResource(properties.getPath());

        return objectMapper.readValue(
                resource.getInputStream(),
                new TypeReference<List<User>>() {}
        );
    }
}