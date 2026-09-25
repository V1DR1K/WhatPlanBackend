package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalBoundaryContractTest {
    @Test
    void personalReviewWritesAreTransactionalInServicesAndNotInControllers() throws Exception {
        assertServiceWrite("update", FilmReviewService.class, Long.class, Long.class, FilmReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", FilmReviewService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("createForView", FilmReviewService.class, Long.class, Long.class,
                FilmReviewRequest.class, com.wherefood.domain.User.class);
        assertServiceWrite("saveLegacy", FilmReviewService.class, Long.class, FilmReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", FilmViewService.class, Long.class, FilmViewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", FilmViewService.class, Long.class, Long.class, FilmViewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", FilmViewService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", FilmCatalogService.class, FilmRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", FilmCatalogService.class, Long.class, FilmRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", FilmCatalogService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("update", CookingReviewService.class, Long.class, CookingReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", CookingReviewService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", RecipeService.class, RecipeRequest.class, com.wherefood.domain.User.class);
        assertServiceWrite("update", RecipeService.class, Long.class, RecipeRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", RecipeService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("replacePhoto", RecipeMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", CookingService.class, Long.class, CookingRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", CookingService.class, Long.class, CookingRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", CookingService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", CookingReviewService.class, Long.class, CookingReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("saveOwn", CookingReviewService.class, Long.class, CookingReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", PlaceVisitReviewService.class, Long.class, PlaceVisitReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", PlaceVisitReviewService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", PlaceVisitReviewService.class, Long.class, PlaceVisitReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("saveOwn", PlaceVisitReviewService.class, Long.class, PlaceVisitReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", PlaceVisitService.class, Long.class, VisitRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", PlaceVisitService.class, Long.class, VisitRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", PlaceVisitService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", ItemService.class, Long.class, CreateItemRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", ItemService.class, Long.class, ItemRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", ItemService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("saveOwn", ItemReviewService.class, Long.class, ItemReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("saveOwn", PlaceReviewService.class, Long.class, PlaceReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", PlaceService.class, PlaceRequest.class, com.wherefood.domain.User.class);
        assertServiceWrite("update", PlaceService.class, Long.class, PlaceRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("archive", PlaceService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("restore", PlaceService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("update", WhyFunVisitReviewService.class, Long.class, ActivityReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", WhyFunVisitReviewService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", WhyFunVisitReviewService.class, Long.class, ActivityReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("saveOwn", WhyFunVisitReviewService.class, Long.class, ActivityReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", WhyFunActivityService.class, ActivityRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", WhyFunActivityService.class, Long.class, ActivityRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", WhyFunActivityService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", WhyFunVisitService.class, Long.class, ActivityVisitRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", WhyFunVisitService.class, Long.class, ActivityVisitRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", WhyFunVisitService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("uploadPlacePhoto", PlaceMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("uploadItemPhoto", PlaceMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("uploadVisitPhoto", PlaceMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("setVisitCover", PlaceMediaService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("deleteVisitPhoto", PlaceMediaService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("uploadVenuePhoto", WhyFunMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("uploadVisitPhoto", WhyFunMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("setVisitCover", WhyFunMediaService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("deleteVisitPhoto", WhyFunMediaService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("uploadPlanPhoto", WhyFunMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("setVenueCover", WhyFunMediaService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("deleteVenuePhoto", WhyFunMediaService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", WhyFunPlanService.class, FunPlanRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", WhyFunPlanService.class, Long.class, FunPlanRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", WhyFunPlanService.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("saveOwnReview", WhyFunPlanService.class, Long.class, FunReviewRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("create", WhyFunCategoryAdminService.class, FunCategoryRequest.class);
        assertServiceWrite("update", WhyFunCategoryAdminService.class, Long.class, FunCategoryRequest.class);
        assertServiceWrite("delete", WhyFunCategoryAdminService.class, Long.class);
        assertServiceWrite("createPlatform", FilmCatalogAdminService.class, PlatformRequest.class);
        assertServiceWrite("updatePlatform", FilmCatalogAdminService.class, Long.class, PlatformRequest.class);
        assertServiceWrite("deletePlatform", FilmCatalogAdminService.class, Long.class);
        assertServiceWrite("createGenre", FilmCatalogAdminService.class, FilmGenreOptionRequest.class);
        assertServiceWrite("updateGenre", FilmCatalogAdminService.class, Long.class,
                FilmGenreOptionRequest.class);
        assertServiceWrite("deleteGenre", FilmCatalogAdminService.class, Long.class);
        assertServiceWrite("createCategory", PlaceCatalogAdminService.class, CategoryRequest.class);
        assertServiceWrite("updateCategory", PlaceCatalogAdminService.class, Long.class,
                CategoryRequest.class);
        assertServiceWrite("deleteCategory", PlaceCatalogAdminService.class, Long.class);
        assertServiceWrite("createTag", PlaceCatalogAdminService.class, HighlightTagRequest.class);
        assertServiceWrite("updateTag", PlaceCatalogAdminService.class, Long.class,
                HighlightTagRequest.class);
        assertServiceWrite("deleteTag", PlaceCatalogAdminService.class, Long.class);
        assertServiceWrite("update", GlobalSettingsService.class, SettingsRequest.class);
        assertServiceWrite("replacePhoto", FilmMediaService.class, Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("create", SpecialDateService.class, SpecialDateRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("update", SpecialDateService.class, Long.class, SpecialDateRequest.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("delete", SpecialDateService.class, Long.class, com.wherefood.domain.User.class);
        assertServiceWrite("saveComment", WhenDateMutationService.class, Long.class, java.time.LocalDate.class,
                WhenDateCommentRequest.class, com.wherefood.domain.User.class);
        assertServiceWrite("deleteComment", WhenDateMutationService.class, Long.class, java.time.LocalDate.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("uploadPhoto", WhenDateMutationService.class, Long.class, java.time.LocalDate.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertServiceWrite("setCover", WhenDateMutationService.class, Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertServiceWrite("deletePhoto", WhenDateMutationService.class, Long.class,
                com.wherefood.domain.User.class);

        assertControllerNotTransactional(FilmApi.class, "updateReview", Long.class, Long.class,
                FilmReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "deleteReview", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "addReview", Long.class, Long.class,
                FilmReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "saveLegacyReview", Long.class,
                FilmReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "addView", Long.class, FilmViewRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "updateView", Long.class, Long.class,
                FilmViewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "deleteView", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "add", FilmRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "update", Long.class, FilmRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "delete", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "uploadPhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(FilmApi.class, "addPlatform", PlatformRequest.class);
        assertControllerNotTransactional(FilmApi.class, "updatePlatform", Long.class, PlatformRequest.class);
        assertControllerNotTransactional(FilmApi.class, "deletePlatform", Long.class);
        assertControllerNotTransactional(FilmApi.class, "addGenre", FilmGenreOptionRequest.class);
        assertControllerNotTransactional(FilmApi.class, "updateGenre", Long.class,
                FilmGenreOptionRequest.class);
        assertControllerNotTransactional(FilmApi.class, "deleteGenre", Long.class);
        assertControllerNotTransactional(Api.class, "uploadPlacePhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "upload", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "uploadVisitPhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "setVisitCover", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "deleteVisitPhoto", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(SpecialDateApi.class, "add", SpecialDateRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(SpecialDateApi.class, "update", Long.class, SpecialDateRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(SpecialDateApi.class, "delete", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhenDatesApi.class, "saveComment", Long.class,
                java.time.LocalDate.class, WhenDateCommentRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhenDatesApi.class, "deleteComment", Long.class,
                java.time.LocalDate.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhenDatesApi.class, "uploadPhoto", Long.class,
                java.time.LocalDate.class, org.springframework.web.multipart.MultipartFile.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhenDatesApi.class, "setCover", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhenDatesApi.class, "deletePhoto", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "updateReview", Long.class,
                CookingReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "deleteReview", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "addReview", Long.class,
                CookingReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "saveOwnReview", Long.class,
                CookingReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "addRecipe", RecipeRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "updateRecipe", Long.class, RecipeRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "deleteRecipe", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "uploadRecipePhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "addCooking", Long.class, CookingRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "updateCooking", Long.class, CookingRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(HomeRecipeApi.class, "deleteCooking", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "updateVisitReview", Long.class,
                PlaceVisitReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "deleteVisitReview", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "addVisitReview", Long.class,
                PlaceVisitReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "saveOwnVisitReview", Long.class,
                PlaceVisitReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "addVisit", Long.class, VisitRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "editVisit", Long.class, VisitRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "deleteVisit", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "addItem", Long.class, CreateItemRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "editItem", Long.class, ItemRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "deleteItem", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "saveItemReview", Long.class, ItemReviewRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "saveReview", Long.class, PlaceReviewRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "addPlace", PlaceRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "editPlace", Long.class, PlaceRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "deletePlace", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "restorePlace", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(Api.class, "addCategory", CategoryRequest.class);
        assertControllerNotTransactional(Api.class, "updateCategory", Long.class, CategoryRequest.class);
        assertControllerNotTransactional(Api.class, "deleteCategory", Long.class);
        assertControllerNotTransactional(Api.class, "addTag", HighlightTagRequest.class);
        assertControllerNotTransactional(Api.class, "updateTag", Long.class, HighlightTagRequest.class);
        assertControllerNotTransactional(Api.class, "deleteTag", Long.class);
        assertControllerNotTransactional(SettingsApi.class, "update", SettingsRequest.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "updateReview", Long.class,
                ActivityReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "deleteReview", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "addReview", Long.class,
                ActivityReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "saveOwnReview", Long.class,
                ActivityReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "addActivity", ActivityRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "updateActivity", Long.class,
                ActivityRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "deleteActivity", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "addVisit", Long.class,
                ActivityVisitRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "updateVisit", Long.class,
                ActivityVisitRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "deleteVisit", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "uploadActivityPhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "uploadPhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "setCover", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunActivityApi.class, "deletePhoto", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "addPlan", FunPlanRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "updatePlan", Long.class, FunPlanRequest.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "deletePlan", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "uploadPhoto", Long.class,
                org.springframework.web.multipart.MultipartFile.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "setCover", Long.class, Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "deletePhoto", Long.class,
                com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "saveReview", Long.class,
                FunReviewRequest.class, com.wherefood.domain.User.class);
        assertControllerNotTransactional(WhyFunApi.class, "addCategory", FunCategoryRequest.class);
        assertControllerNotTransactional(WhyFunApi.class, "updateCategory", Long.class,
                FunCategoryRequest.class);
        assertControllerNotTransactional(WhyFunApi.class, "deleteCategory", Long.class);
    }

    private static void assertServiceWrite(String name, Class<?> service, Class<?>... parameters)
            throws NoSuchMethodException {
        assertTrue(service.getMethod(name, parameters).isAnnotationPresent(Transactional.class),
                () -> service.getSimpleName() + "." + name + " must own its write transaction");
    }

    private static void assertControllerNotTransactional(String name, Class<?> controller, Class<?>... parameters)
            throws NoSuchMethodException {
        assertFalse(controller.getDeclaredMethod(name, parameters).isAnnotationPresent(Transactional.class),
                () -> controller.getSimpleName() + "." + name + " must delegate its transaction to a service");
    }
}
