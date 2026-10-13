package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements CommandLineRunner {

    private final DataReader dataReader;
    private final DataProperties properties;

    public StartupRunner(
            DataReader dataReader,
            DataProperties properties
    ) {
        this.dataReader = dataReader;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {

        System.out.println("Reading from: " + properties.getPath());
        System.out.println("Format: " + properties.getFormat());

        dataReader.readData()
                .stream()
                .limit(properties.getLimit())
                .forEach(System.out::println);
    }
}