package com.osman.traviaskbot.service;

import com.osman.traviaskbot.entity.Driver;
import com.osman.traviaskbot.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;

    public List<Driver> getAll() {
        return driverRepository.findAll();
    }

    public Driver save(Driver driver) {
        return driverRepository.save(driver);
    }

    public void deleteById(Long id) {
        driverRepository.deleteById(id);
    }
}
