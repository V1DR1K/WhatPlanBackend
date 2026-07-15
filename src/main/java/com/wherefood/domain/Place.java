package com.wherefood.domain;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="places") public class Place { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false) public String name; public String zone; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="category_id") public Category category; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") public User createdBy; public Instant createdAt; public Instant updatedAt; }
