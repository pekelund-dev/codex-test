package dev.pekelund.pklnd.function;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptProcessingController.class)
class ReceiptProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReceiptParsingHandler receiptParsingHandler;

    private StorageObjectEvent event;

    @BeforeEach
    void setUp() {
        event = new StorageObjectEvent();
        event.setBucket("bucket");
        event.setName("receipts/sample.pdf");
    }

    @Test
    void delegatesToHandlerWhenPayloadValid() throws Exception {
        String payload = objectMapper.writeValueAsString(event);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/events/storage")
                .contentType(MediaType.APPLICATION_JSON)
                .header("ce-type", "google.cloud.storage.object.v1.finalized")
                .content(payload))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());

        verify(receiptParsingHandler).handle(Mockito.argThat(arg ->
            "bucket".equals(arg.getBucket()) && "receipts/sample.pdf".equals(arg.getName())));
    }

    @Test
    void returnsBadRequestForEmptyBody() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/events/storage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        verifyNoInteractions(receiptParsingHandler);
    }
}
