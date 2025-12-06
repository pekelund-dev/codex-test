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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReceiptParsingApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReceiptParserRegistry receiptParserRegistry;

    @InjectMocks
    private ReceiptParsingApiController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

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
        when(receiptParserRegistry.find("hybrid")).thenReturn(Optional.of(new ParserRegistration(descriptor, extractor)));

        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/hybrid/parse")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parser.id").value("hybrid"))
            .andExpect(jsonPath("$.structuredData.ok").value(true));
    }

    @Test
    void returnsNotFoundForUnknownParser() throws Exception {
        when(receiptParserRegistry.find("unknown")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/unknown/parse")
                .file(file))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonPdfUploads() throws Exception {
        ReceiptParserDescriptor descriptor = new ReceiptParserDescriptor("hybrid", "Hybrid", "Combined parser");
        ReceiptDataExtractor extractor = Mockito.mock(ReceiptDataExtractor.class);
        when(receiptParserRegistry.find("hybrid")).thenReturn(Optional.of(new ParserRegistration(descriptor, extractor)));

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "test".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/parsers/hybrid/parse")
                .file(file))
            .andExpect(status().isBadRequest());
    }
}
