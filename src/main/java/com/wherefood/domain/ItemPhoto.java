package com.wherefood.domain;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="item_photos") public class ItemPhoto { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_id") public Item item; public String objectKey; public String thumbnailKey; public int width; public int height; public Instant createdAt; }
