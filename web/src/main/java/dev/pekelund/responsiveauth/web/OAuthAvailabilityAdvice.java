package dev.pekelund.responsiveauth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class OAuthAvailabilityAdvice {

    private static final Logger log = LoggerFactory.getLogger(OAuthAvailabilityAdvice.class);

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    public OAuthAvailabilityAdvice(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) {
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
    }

    @ModelAttribute("oauthEnabled")
    public boolean oauthEnabled() {
        ClientRegistrationRepository repository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("No ClientRegistrationRepository available; OAuth login disabled");
            return false;
        }
        if (repository instanceof Iterable<?> iterable) {
            boolean hasClient = iterable.iterator().hasNext();
            if (!hasClient) {
                log.warn("OAuth ClientRegistrationRepository is present but contains no registrations");
            }
            return hasClient;
        }
        return true;
    }
}
