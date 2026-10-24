package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.MenuApi;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maps every Menu endpoint from openapi.yaml. Until a method is overridden here,
 * the generated default implementation answers 501 Not Implemented.
 */
@RestController
public class MenuController implements MenuApi {
}
