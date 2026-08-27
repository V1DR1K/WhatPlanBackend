package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="item_reviews", uniqueConstraints=@UniqueConstraint(columnNames={"item_id", "author_id"}))
public class ItemReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_id", nullable=false) public Item item;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="author_id", nullable=false) public User author;
 public String comment;
 @Column(nullable=false) public short taste;
 @Column(nullable=false) public short price;
 public Instant createdAt;
 public Instant updatedAt;
}
