package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "why_fun_categories")
public class WhyFunCategory {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id") public WhyFunCategory parent;
 @Column(nullable = false) public String name;
 @Column(nullable = false) public String slug;
 @Column(nullable = false) public String icon;
 @Column(nullable = false) public boolean active = true;
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
