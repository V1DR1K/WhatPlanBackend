package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Item;
import com.wherefood.domain.ItemReview;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.ItemReviews;
import com.wherefood.repo.Repositories.Items;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemReviewServiceTest {
    private final Items items = mock(Items.class);
    private final ItemReviews reviews = mock(ItemReviews.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final ItemReviewService service = new ItemReviewService(items, reviews,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishCoupleContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(8L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void saveOwn_whenSecondMemberReviewsItem_createsReviewForAuthenticatedAuthor() {
        User secondMember = user(8L);
        PlaceVisit visit = new PlaceVisit();
        visit.place = new Place();
        Item item = new Item();
        item.id = 14L;
        item.visit = visit;
        when(items.findByIdAndCoupleId(item.id, coupleId)).thenReturn(Optional.of(item));
        when(reviews.findByItemIdAndAuthorIdAndCoupleId(item.id, secondMember.id, coupleId))
                .thenReturn(Optional.empty());
        when(reviews.save(any(ItemReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemReview saved = service.saveOwn(item.id, new ItemReviewRequest("La pediría de nuevo", (short) 5, (short) 4), secondMember);

        assertEquals(secondMember, saved.author);
        assertEquals(item, saved.item);
        assertEquals("La pediría de nuevo", saved.comment);
        verify(reviews).findByItemIdAndAuthorIdAndCoupleId(item.id, secondMember.id, coupleId);
        verify(reviews).save(saved);
    }

    private static User user(Long id) {
        User user = new User();
        user.id = id;
        user.role = Role.USER;
        return user;
    }
}
