package dev.pekelund.pklnd.receiptparser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.receiptparser.ReceiptParserRegistry.ParserRegistration;
import dev.pekelund.pklnd.receiptparser.ReceiptParserRegistry.ReceiptParserDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(ReceiptParsingApiController.class)
class ReceiptParsingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptParserRegistry receiptParserRegistry;

    @Test
    void listsSupportedParsers() throws Exception {
        ReceiptParserDescriptor descriptor = new ReceiptParserDescriptor("hybrid", "Hybrid", "Combined parser");
        when(receiptParserRegistry.listParsers()).thenReturn(List.of(descriptor));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/parsers").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("hybrid"))
            .andExpect(jsonPath("$[0].displayName").value("Hybrid"));
    }

    @Test
    void parsesReceiptWithSelectedParser() throws Exception {
        ReceiptParserDescriptor descriptor = new ReceiptParserDescriptor("hybrid", "Hybrid", "Combined parser");
        ReceiptDataExtractor extractor = Mockito.mock(ReceiptDataExtractor.class);
        when(extractor.extract(any(), eq("receipt.pdf"))).thenReturn(new ReceiptExtractionResult(Map.of("ok", true), "{}"));
        when(receiptParserRegistry.find("hybrid")).thenReturn(java.util.Optional.of(new ParserRegistration(descriptor, extractor)));

        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/hybrid/parse")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parser.id").value("hybrid"))
            .andExpect(jsonPath("$.structuredData.ok").value(true));
    }

    @Test
    void returnsNotFoundForUnknownParser() throws Exception {
        when(receiptParserRegistry.find("unknown")).thenReturn(java.util.Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/unknown/parse")
                .file(file))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonPdfUploads() throws Exception {
        ReceiptParserDescriptor descriptor = new ReceiptParserDescriptor("hybrid", "Hybrid", "Combined parser");
        ReceiptDataExtractor extractor = Mockito.mock(ReceiptDataExtractor.class);
        when(receiptParserRegistry.find("hybrid")).thenReturn(java.util.Optional.of(new ParserRegistration(descriptor, extractor)));

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/hybrid/parse")
                .file(file))
            .andExpect(status().isBadRequest());
    }
}
