package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wherefood.domain.GlobalSettings;
import com.wherefood.repo.Repositories.Settings;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettingsApiTest {
 @Test
 void returnsAndRecreatesTheDefaultWhenTheSingletonIsMissing() {
  Settings settings = mock(Settings.class);
  when(settings.findById(1)).thenReturn(Optional.empty(), Optional.of(settings(5)));

  SettingsDto result = new SettingsApi(settings).get();

  assertEquals(5, result.catalogPageSize());
  verify(settings).insertDefaultIfMissing();
 }

 @Test
 void readsThePersistedCatalogPageSize() {
  Settings settings = mock(Settings.class);
  when(settings.findById(1)).thenReturn(Optional.of(settings(12)));

  assertEquals(12, new SettingsApi(settings).get().catalogPageSize());
 }

 @Test
 void updatesThePersistedCatalogPageSize() {
  Settings settings = mock(Settings.class);
  GlobalSettings value = settings(5);
  when(settings.findById(1)).thenReturn(Optional.of(value));
  when(settings.save(any(GlobalSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

  SettingsDto result = new SettingsApi(settings).update(new SettingsRequest(20));

  assertEquals(20, result.catalogPageSize());
  verify(settings).save(value);
 }

 @Test
 void validatesTheCatalogPageSizeRange() throws Exception {
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SettingsApi(mock(Settings.class))).build();

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
