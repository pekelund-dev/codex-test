package dev.pekelund.pklnd.receiptparser.legacy.archive;

import dev.pekelund.pklnd.receiptparser.legacy.ReceiptParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @deprecated Prefer using {@link dev.pekelund.pklnd.receiptparser.legacy.ReceiptParser}.
 * This class is retained to keep compatibility with historical deployments.
 */
@Deprecated
@Component("legacyNewFormatParser")
@Profile("legacy-new-format")
public class NewFormatParser extends ReceiptParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewFormatParser.class);

    public NewFormatParser() {
        LOGGER.warn("Using legacy NewFormatParser bean. Please migrate to ReceiptParser.");
    }
}
