package com.example.demo;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class DataInfoContributor implements InfoContributor {

    private final DataProperties properties;

    public DataInfoContributor(DataProperties properties) {
        this.properties = properties;
    }

    @Override
    public void contribute(Info.Builder builder) {

        builder.withDetail("dataSource", properties.getPath())
                .withDetail("format", properties.getFormat())
                .withDetail("limit", properties.getLimit());
    }
}