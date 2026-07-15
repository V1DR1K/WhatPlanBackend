package com.wherefood.domain;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="items") public class Item { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="place_id") public Place place; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="author_id") public User author; @Column(nullable=false) public String name; public String comment; public short taste; public short price; @Column(name="venue") public short venue; public Instant createdAt; public Instant updatedAt; }
