package com.osman.traviaskbot.repository;


import com.osman.traviaskbot.entity.ChangedMail;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ChangedMailRepository extends JpaRepository<ChangedMail,Long> {}
