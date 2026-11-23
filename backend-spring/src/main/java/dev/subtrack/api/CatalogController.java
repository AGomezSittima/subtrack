package dev.subtrack.api;

import dev.subtrack.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
class CatalogController {

  private final CatalogService catalog;

  CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  CatalogServiceList list(HttpServletRequest request) {
    Map<String, String> query = QueryString.parse(request.getQueryString());
    QueryParameters parameters = new QueryParameters();
    String search = parameters.text("q", query.get("q"), 1, 100);
    boolean hidden = parameters.bool("include_hidden", query.get("include_hidden"), false);
    parameters.check();
    return new CatalogServiceList(
        catalog.search(search, hidden).stream().map(CatalogServiceResponse::of).toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CatalogServiceResponse create(@Valid @RequestBody CatalogServiceCreateRequest body) {
    return CatalogServiceResponse.of(catalog.create(body.entry()));
  }

  @PatchMapping("/{id}")
  CatalogServiceResponse update(
      @PathVariable("id") String id, @Valid @RequestBody CatalogServiceUpdateRequest body) {
    return CatalogServiceResponse.of(catalog.update(PathIds.parse(id), body.changes()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable("id") String id) {
    catalog.delete(PathIds.parse(id));
  }
}
