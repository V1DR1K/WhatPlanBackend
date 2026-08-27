package com.wherefood.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

record TmdbCastMemberDto(String name, String character, String profileUrl) {}
record TmdbMovieDto(Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterUrl, String posterThumbnailUrl, String posterFullUrl, List<String> genres, Integer runtime, String director, String trailerUrl, Double voteAverage, Integer voteCount, List<TmdbCastMemberDto> cast) {}

@Component
class TmdbClient {
 private static final String API_URL = "https://api.themoviedb.org/3";
 private static final String IMAGE_URL = "https://image.tmdb.org/t/p/";
  private static final long DEFAULT_CACHE_SECONDS = 21_600;
  private static final int DEFAULT_MAX_CACHE_ENTRIES = 512;
  private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
  private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 8;
  private static final int MAX_RESPONSE_CHARS = 2_000_000;

 private final String token;
 private final ObjectMapper json;
  private final HttpClient http;
  private final long requestTimeoutSeconds;
  private final long cacheSeconds;
  private final BoundedCache<Long, CachedMovie> summaries;
  private final BoundedCache<Long, CachedMovie> details;
  private final BoundedCache<Long, CachedMovies> recommendations;

  TmdbClient(String token, ObjectMapper json) {
   this(token, json, DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_REQUEST_TIMEOUT_SECONDS, DEFAULT_CACHE_SECONDS, DEFAULT_MAX_CACHE_ENTRIES);
  }

  @org.springframework.beans.factory.annotation.Autowired
  TmdbClient(@Value("${app.tmdb.read-access-token}") String token, ObjectMapper json,
             @Value("${app.tmdb.connect-timeout-seconds:5}") long connectTimeoutSeconds,
             @Value("${app.tmdb.request-timeout-seconds:8}") long requestTimeoutSeconds,
             @Value("${app.tmdb.cache-ttl-seconds:21600}") long cacheSeconds,
             @Value("${app.tmdb.max-cache-entries:512}") int maxCacheEntries) {
   if (connectTimeoutSeconds <= 0 || requestTimeoutSeconds <= 0 || cacheSeconds <= 0 || maxCacheEntries <= 0) {
    throw new IllegalArgumentException("TMDB limits must be positive");
   }
   this.token = token;
   this.json = json;
   this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
   this.requestTimeoutSeconds = requestTimeoutSeconds;
   this.cacheSeconds = cacheSeconds;
   this.summaries = new BoundedCache<>(maxCacheEntries);
   this.details = new BoundedCache<>(maxCacheEntries);
   this.recommendations = new BoundedCache<>(maxCacheEntries);
 }

 List<TmdbMovieDto> search(String query) {
  if (query == null || query.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribí el nombre de una película");
  if (query.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La búsqueda es demasiado larga");
  JsonNode results = request("/search/movie?language=es-AR&include_adult=false&page=1&query=" + encode(query.trim())).path("results");
  List<TmdbMovieDto> movies = new ArrayList<>();
  for (JsonNode movie : results) {
   if (movie.path("id").canConvertToLong()) movies.add(movie(movie, List.of(), null, null, null, List.of()));
   if (movies.size() == 12) break;
  }
  return movies;
 }

  TmdbMovieDto summary(long tmdbId) {
   requirePositiveId(tmdbId);
   CachedMovie cached = summaries.get(tmdbId);
   if (cached != null && cached.valid()) return cached.movie();
   if (cached != null) summaries.remove(tmdbId);
  JsonNode movie = movie(tmdbId, false);
  TmdbMovieDto result = movie(movie, names(movie.path("genres")), number(movie, "runtime"), null, null, List.of());
   summaries.put(tmdbId, new CachedMovie(result, cacheSeconds));
  return result;
 }

  TmdbMovieDto details(long tmdbId) {
   requirePositiveId(tmdbId);
   CachedMovie cached = details.get(tmdbId);
   if (cached != null && cached.valid()) return cached.movie();
   if (cached != null) details.remove(tmdbId);
  JsonNode movie = movie(tmdbId, true);
  TmdbMovieDto result = movie(movie, names(movie.path("genres")), number(movie, "runtime"), director(movie.path("credits").path("crew")), trailer(movie.path("videos").path("results")), cast(movie.path("credits").path("cast")));
   details.put(tmdbId, new CachedMovie(result, cacheSeconds));
  return result;
 }

  List<TmdbMovieDto> recommendations(long tmdbId) {
    requirePositiveId(tmdbId);
    CachedMovies cached = recommendations.get(tmdbId);
    if (cached != null && cached.valid()) return cached.movies();
    if (cached != null) recommendations.remove(tmdbId);
   JsonNode results = request("/movie/" + tmdbId + "/recommendations?language=es-AR&page=1").path("results");
   List<TmdbMovieDto> movies = new ArrayList<>();
   for (JsonNode movie : results) {
    if (movie.path("id").canConvertToLong()) movies.add(movie(movie, List.of(), null, null, null, List.of()));
    if (movies.size() == 12) break;
   }
   List<TmdbMovieDto> result = List.copyOf(movies);
    recommendations.put(tmdbId, new CachedMovies(result, cacheSeconds));
   return result;
  }

  private JsonNode movie(long tmdbId, boolean includeCredits) {
  JsonNode movie = request("/movie/" + tmdbId + "?language=es-AR" + (includeCredits ? "&append_to_response=credits,videos" : ""));
  if (!movie.path("id").canConvertToLong()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos esa película en TMDB");
  return movie;
 }

 private TmdbMovieDto movie(JsonNode movie, List<String> genres, Integer runtime, String director, String trailerUrl, List<TmdbCastMemberDto> cast) {
  String posterPath = text(movie, "poster_path");
   return new TmdbMovieDto(movie.path("id").asLong(), text(movie, "title"), text(movie, "original_title"), text(movie, "overview"), date(text(movie, "release_date")), image(posterPath, "w342"), image(posterPath, "w342"), image(posterPath, "w780"), genres, runtime, director, trailerUrl, decimal(movie, "vote_average"), number(movie, "vote_count"), cast);
 }

 private JsonNode request(String path) {
  if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El buscador de películas todavía no está configurado");
  try {
    HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL + path)).timeout(Duration.ofSeconds(requestTimeoutSeconds)).header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
   if (response.statusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos esa película en TMDB");
    if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB no respondió en este momento");
     if (response.body().length() > MAX_RESPONSE_CHARS) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La respuesta de TMDB es demasiado grande");
    return json.readTree(response.body());
   } catch (ResponseStatusException error) {
    throw error;
   } catch (Exception error) {
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos conectar con TMDB ahora", error);
   }
  }

   private static void requirePositiveId(long tmdbId) {
    if (tmdbId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de TMDB no es válido");
   }

 private static List<String> names(JsonNode values) {
  List<String> names = new ArrayList<>();
  for (JsonNode value : values) if (text(value, "name") != null) names.add(text(value, "name"));
  return names;
 }

 private static String director(JsonNode crew) {
  for (JsonNode person : crew) if ("Director".equals(person.path("job").asText()) && text(person, "name") != null) return text(person, "name");
  return null;
 }

 private static String trailer(JsonNode videos) {
  for (JsonNode video : videos) if ("YouTube".equals(video.path("site").asText()) && "Trailer".equals(video.path("type").asText()) && video.path("official").asBoolean()) return "https://www.youtube.com/watch?v=" + video.path("key").asText();
  for (JsonNode video : videos) if ("YouTube".equals(video.path("site").asText()) && "Trailer".equals(video.path("type").asText())) return "https://www.youtube.com/watch?v=" + video.path("key").asText();
  return null;
 }

 private static List<TmdbCastMemberDto> cast(JsonNode cast) {
  List<TmdbCastMemberDto> members = new ArrayList<>();
  for (JsonNode person : cast) {
   String name = text(person, "name");
   if (name != null) members.add(new TmdbCastMemberDto(name, text(person, "character"), image(text(person, "profile_path"), "w185")));
   if (members.size() == 8) break;
  }
  return members;
 }

 private static String image(String path, String size) { return path == null ? null : IMAGE_URL + size + path; }
 private static String text(JsonNode value, String field) { String text = value.path(field).asText(); return text.isBlank() ? null : text; }
 private static Integer number(JsonNode value, String field) { return value.path(field).canConvertToInt() ? value.path(field).asInt() : null; }
 private static Double decimal(JsonNode value, String field) { return value.path(field).isNumber() ? value.path(field).asDouble() : null; }
 private static LocalDate date(String value) { try { return value == null ? null : LocalDate.parse(value); } catch (Exception ignored) { return null; } }
 private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

  private record CachedMovie(TmdbMovieDto movie, Instant expiresAt) {
   CachedMovie(TmdbMovieDto movie, long cacheSeconds) { this(movie, Instant.now().plusSeconds(cacheSeconds)); }
  boolean valid() { return expiresAt.isAfter(Instant.now()); }
 }
 private record CachedMovies(List<TmdbMovieDto> movies, Instant expiresAt) {
   CachedMovies(List<TmdbMovieDto> movies, long cacheSeconds) { this(movies, Instant.now().plusSeconds(cacheSeconds)); }
  boolean valid() { return expiresAt.isAfter(Instant.now()); }
  }

  private static final class BoundedCache<K, V> {
   private final int maxEntries;
   private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, .75f, true);

   BoundedCache(int maxEntries) { this.maxEntries = maxEntries; }

   synchronized V get(K key) { return values.get(key); }

   synchronized void remove(K key) { values.remove(key); }

   synchronized void put(K key, V value) {
    values.put(key, value);
    while (values.size() > maxEntries) values.remove(values.keySet().iterator().next());
   }
  }
}
