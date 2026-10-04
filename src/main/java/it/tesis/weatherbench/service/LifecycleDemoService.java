package it.tesis.weatherbench.service;

import java.util.Map;

public interface LifecycleDemoService {

    Map<String, Object> jpaPersistenceContextDemo();

    Map<String, Object> mongoStatelessDemo();
}
