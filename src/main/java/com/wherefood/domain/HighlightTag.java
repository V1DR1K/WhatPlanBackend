package com.wherefood.domain;
import jakarta.persistence.*;
@Entity @Table(name="highlight_tags") public class HighlightTag { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false, unique=true, length=60) public String name; @Column(nullable=false, length=20) public String emoji; }
