package com.wherefood.web;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

record CatalogFilmDto(Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterPath, List<String> genres) {}

@Component
class TmdbClient {
 private final String token;
 private final ObjectMapper json;
 private final HttpClient http = HttpClient.newHttpClient();

 TmdbClient(@Value("${app.tmdb.read-token:}") String token, ObjectMapper json) {
  this.token = token;
  this.json = json;
 }

 List<CatalogFilmDto> search(String query) {
  if (query == null || query.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribí el nombre de una película");
  JsonNode results = request("/search/movie?language=es-AR&include_adult=false&query=" + encode(query.trim())).path("results");
  List<CatalogFilmDto> films = new ArrayList<>();
  for (JsonNode movie : results) {
   if (movie.path("id").canConvertToLong()) films.add(movie(movie, List.of()));
   if (films.size() == 12) break;
  }
  return films;
 }

 CatalogFilmDto details(long tmdbId) {
  JsonNode movie = request("/movie/" + tmdbId + "?language=es-AR");
  if (movie.isMissingNode() || !movie.path("id").canConvertToLong()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos esa película en TMDB");
  List<String> genres = new ArrayList<>();
  for (JsonNode genre : movie.path("genres")) if (!genre.path("name").asText().isBlank()) genres.add(genre.path("name").asText());
  return movie(movie, genres);
 }

 private CatalogFilmDto movie(JsonNode movie, List<String> genres) {
  return new CatalogFilmDto(movie.path("id").asLong(), text(movie, "title"), text(movie, "original_title"), text(movie, "overview"), date(text(movie, "release_date")), text(movie, "poster_path"), genres);
 }

 private JsonNode request(String path) {
  if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El buscador de películas todavía no está configurado");
  try {
   HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.themoviedb.org/3" + path)).header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build();
   HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
   if (response.statusCode() == 404) return json.createObjectNode();
   if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB no respondió en este momento");
   return json.readTree(response.body());
  } catch (ResponseStatusException error) {
   throw error;
  } catch (Exception error) {
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos conectar con TMDB ahora", error);
  }
 }

 private static String text(JsonNode value, String field) { String text = value.path(field).asText(); return text.isBlank() ? null : text; }
 private static LocalDate date(String value) { try { return value == null ? null : LocalDate.parse(value); } catch (Exception ignored) { return null; } }
 private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
