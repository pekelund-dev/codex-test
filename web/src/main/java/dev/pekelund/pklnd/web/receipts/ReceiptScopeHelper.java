package dev.pekelund.pklnd.web.receipts;

import dev.pekelund.pklnd.storage.ReceiptStorageException;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReceiptScopeHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptScopeHelper.class);
    private static final String SCOPE_MY = "my";
    private static final String SCOPE_ALL = "all";

    public ReceiptViewScope resolveScope(String scopeParam, Authentication authentication) {
        if (scopeParam != null && SCOPE_ALL.equalsIgnoreCase(scopeParam) && isAdmin(authentication)) {
            return ReceiptViewScope.ALL;
        }
        return ReceiptViewScope.MY;
    }

    public boolean isViewingAll(ReceiptViewScope scope, Authentication authentication) {
        return scope == ReceiptViewScope.ALL && isAdmin(authentication);
    }

    public boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN");
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public String toScopeParameter(ReceiptViewScope scope) {
        return scope == ReceiptViewScope.ALL ? SCOPE_ALL : SCOPE_MY;
    }

    public boolean receiptFileExists(Optional<ReceiptStorageService> receiptStorageService, String objectName) {
        if (!StringUtils.hasText(objectName) || receiptStorageService.isEmpty()
            || !receiptStorageService.get().isEnabled()) {
            return false;
        }
        try {
            return receiptStorageService.get().fileExists(objectName);
        } catch (ReceiptStorageException ex) {
            LOGGER.warn("Unable to verify receipt file existence for {}", objectName, ex);
            return false;
        }
    }
}
