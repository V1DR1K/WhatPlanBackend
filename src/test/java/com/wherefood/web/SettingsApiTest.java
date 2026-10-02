package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wherefood.domain.GlobalSettings;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettingsApiTest {
 @Test
 void returnsAndRecreatesTheDefaultWhenTheSingletonIsMissing() {
  GlobalSettingsService service = mock(GlobalSettingsService.class);
  when(service.get()).thenReturn(settings(5));

  SettingsDto result = new SettingsApi(service).get();

  assertEquals(5, result.catalogPageSize());
  verify(service).get();
 }

 @Test
 void readsThePersistedCatalogPageSize() {
  GlobalSettingsService service = mock(GlobalSettingsService.class);
  when(service.get()).thenReturn(settings(12));

  assertEquals(12, new SettingsApi(service).get().catalogPageSize());
 }

 @Test
 void updatesThePersistedCatalogPageSize() {
  GlobalSettingsService service = mock(GlobalSettingsService.class);
  GlobalSettings value = settings(20);
  when(service.update(any(SettingsRequest.class))).thenReturn(value);

  SettingsDto result = new SettingsApi(service).update(new SettingsRequest(20));

  assertEquals(20, result.catalogPageSize());
  verify(service).update(new SettingsRequest(20));
 }

 @Test
 void validatesTheCatalogPageSizeRange() throws Exception {
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SettingsApi(mock(GlobalSettingsService.class))).build();

  mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"catalogPageSize\":0}"))
    .andExpect(status().isBadRequest());
  mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"catalogPageSize\":51}"))
    .andExpect(status().isBadRequest());
 }

 @Test
 void limitsSettingsAccessToAuthenticatedUsersAndAdmins() throws Exception {
  assertAccess("get", "isAuthenticated()");
  assertAccess("update", "hasRole('ADMIN')", SettingsRequest.class);
 }

 private static void assertAccess(String name, String expression, Class<?>... parameterTypes) throws NoSuchMethodException {
  Method method = SettingsApi.class.getDeclaredMethod(name, parameterTypes);
  assertEquals(expression, method.getAnnotation(PreAuthorize.class).value());
 }

 private static GlobalSettings settings(int catalogPageSize) {
  GlobalSettings value = new GlobalSettings();
  value.id = 1;
  value.catalogPageSize = catalogPageSize;
  return value;
 }
}
