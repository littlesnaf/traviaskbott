package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.CancelledMail;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CancelledMailRepository extends JpaRepository<CancelledMail,Long> {}
