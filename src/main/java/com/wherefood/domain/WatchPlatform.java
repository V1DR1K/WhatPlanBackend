package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "watch_platforms")
public class WatchPlatform {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Column(nullable = false, unique = true) public String name;
 @Column(nullable = false) public String icon;
 public boolean active = true;
 public Instant createdAt;
}
