package com.example.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
@ConditionalOnExpression("'${app.data.format}' == 'csv'")
@RequiredArgsConstructor
public class CsvDataReader implements DataReader {

    private final DataProperties properties;
    private final ResourceLoader resourceLoader;

    @Override
    public List<User> readData() throws Exception {
        Resource resource = resourceLoader.getResource(properties.getPath());

        List<User> users = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            reader.readLine(); // Skip header

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(",");

                users.add(new User(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2]
                ));
            }
        }

        return users;
    }
}
