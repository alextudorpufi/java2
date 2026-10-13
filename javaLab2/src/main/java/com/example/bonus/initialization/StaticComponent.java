package com.example.bonus.initialization;

import com.example.bonus.MyComponent;

@MyComponent
public class StaticComponent {
    static {
        System.setProperty("bonus.static.initialized", "true");
    }
}
