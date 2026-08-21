package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

 @Entity
 @Table(name="users")
 public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,unique=true,length=80) public String username;
  @Column(name="auth_user_id",unique=true) public java.util.UUID authUserId;
  @Column(name="password_hash") public String passwordHash;
  @Enumerated(EnumType.STRING) public Role role;
 public Instant createdAt;

 @PrePersist void initializeCreatedAt() { if (createdAt == null) createdAt = Instant.now(); }
}
