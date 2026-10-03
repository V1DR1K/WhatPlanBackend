package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_packing_items")
public class JourneyPackingItem extends JourneyEntity {
    public java.util.UUID journeyId;
    public Long userId;
    public Long memberId;
    public String description;
    public int quantity = 1;
    public boolean packed;
}
