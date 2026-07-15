package com.wherefood.domain;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="users") public class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false,unique=true) public String username; @Column(name="password_hash",nullable=false) public String passwordHash; @Enumerated(EnumType.STRING) public Role role; public Instant createdAt; }
