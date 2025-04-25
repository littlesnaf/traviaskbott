package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {
}
