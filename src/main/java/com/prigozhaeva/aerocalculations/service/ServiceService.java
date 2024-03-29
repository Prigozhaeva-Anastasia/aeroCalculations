package com.prigozhaeva.aerocalculations.service;

import com.prigozhaeva.aerocalculations.entity.Service;

import java.util.List;

public interface ServiceService {
    List<Service> findServicesByServiceName(String serviceName);
    void importServices(String path);
    Service findServiceById(Long id);
    Service createOrUpdateService(Service service);
    List<Service> fetchAll();
}
