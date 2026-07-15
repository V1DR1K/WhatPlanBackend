package com.wherefood.domain;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="categories") public class Category { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false,unique=true) public String name; @Column(nullable=false,unique=true) public String slug; @Column(nullable=false) public String icon; public boolean active=true; public Instant createdAt; }
