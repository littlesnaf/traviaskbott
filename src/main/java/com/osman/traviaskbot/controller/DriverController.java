package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.entity.Driver;
import com.osman.traviaskbot.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAll();
    }

    @PostMapping
    public Driver createDriver(@RequestBody Driver driver) {
        return driverService.save(driver);
    }

    @DeleteMapping("/{id}")
    public void deleteDriver(@PathVariable Long id) {
        driverService.deleteById(id);
    }
}
