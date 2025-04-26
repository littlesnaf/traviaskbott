package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.UnparsedMail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnparsedMailRepository extends JpaRepository<UnparsedMail, Long> { }
