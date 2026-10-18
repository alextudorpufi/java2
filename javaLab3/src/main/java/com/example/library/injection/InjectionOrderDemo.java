package com.example.library.injection;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class InjectionOrderDemo {
    private static final Logger log = LoggerFactory.getLogger(InjectionOrderDemo.class);
    private final List<String> observations = new ArrayList<>();
    private final InjectionDependency constructorDependency;
    @Autowired
    private InjectionDependency fieldDependency;
    private InjectionDependency setterDependency;
    private InjectionDependency methodDependency;

    public InjectionOrderDemo(InjectionDependency dependency) {
        constructorDependency = dependency;
        require(fieldDependency == null, "Field has not been injected in constructor");
        observations.add("constructor: dependency present, field absent");
    }

    @Autowired
    public void setDependency(InjectionDependency dependency) {
        observeField("setter");
        setterDependency = dependency;
    }

    @Autowired
    public void configure(InjectionDependency dependency) {
        observeField("method");
        methodDependency = dependency;
    }

    private void observeField(String injectionPoint) {
        require(constructorDependency != null && fieldDependency != null,
                "Constructor and field injection must precede methods");
        if (observations.size() == 1) {
            observations.add("field: dependency present before methods");
        }
        observations.add(injectionPoint + ": field already present");
    }

    @PostConstruct
    void verify() {
        require(setterDependency != null && methodDependency != null, "All dependencies injected");
        observations.forEach(step -> log.info("Injection demo: {}", step));
    }

    public List<String> observations() { return List.copyOf(observations); }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
