package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "global_settings")
public class GlobalSettings {
 @Id public Integer id;
 @Column(name = "catalog_page_size", nullable = false) public int catalogPageSize = 5;
}
