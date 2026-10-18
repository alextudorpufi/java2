package com.example.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.data")
@Setter
@Getter
public class DataProperties {
    private String path;
    private String format;
    private int limit;


}
