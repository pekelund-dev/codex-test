package dev.pekelund.pklnd.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles the demo-mode entry point.
 * Visiting {@code /demo} initialises a demo session and redirects the user to the
 * receipt-uploads page so they can immediately try the upload feature.
 */
@Controller
public class DemoController {

    private final DemoSessionService demoSessionService;

    public DemoController(DemoSessionService demoSessionService) {
        this.demoSessionService = demoSessionService;
    }

    @GetMapping("/demo")
    public String startDemo(HttpSession session) {
        demoSessionService.getOrCreateDemoOwner(session);
        return "redirect:/receipts/uploads";
    }
}
