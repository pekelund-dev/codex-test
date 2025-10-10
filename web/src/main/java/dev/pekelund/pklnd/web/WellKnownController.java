package dev.pekelund.pklnd.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles requests to ".well-known" paths that certain browsers may call automatically
 * after authentication. Responding with an empty payload prevents unnecessary 404
 * responses that would otherwise invoke the generic error page.
 */
@RestController
@RequestMapping("/.well-known/appspecific")
public class WellKnownController {

    private static final String CHROME_DEVTOOLS_PATH = "/com.chrome.devtools.json";

    @GetMapping(CHROME_DEVTOOLS_PATH)
    public ResponseEntity<Void> chromeDevTools() {
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = CHROME_DEVTOOLS_PATH, method = RequestMethod.HEAD)
    public ResponseEntity<Void> chromeDevToolsHead() {
        return ResponseEntity.noContent().build();
    }
}
