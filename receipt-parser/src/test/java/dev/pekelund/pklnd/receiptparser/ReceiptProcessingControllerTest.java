package dev.pekelund.pklnd.receiptparser;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private ReceiptParsingHandler receiptParsingHandler;

    private ReceiptProcessingController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new ReceiptProcessingController(objectMapper, receiptParsingHandler);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void delegatesToHandlerWhenPayloadValid() throws Exception {
        StorageObjectEvent event = new StorageObjectEvent();
        event.setBucket("bucket");
        event.setName("receipts/sample.pdf");
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
    void delegatesToHandlerWhenPostedToRootPath() throws Exception {
        StorageObjectEvent event = new StorageObjectEvent();
        event.setBucket("bucket");
        event.setName("receipts/alt.pdf");
        String payload = objectMapper.writeValueAsString(event);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("ce-type", "google.cloud.storage.object.v1.finalized")
                .content(payload))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());

        verify(receiptParsingHandler).handle(Mockito.argThat(arg ->
            "bucket".equals(arg.getBucket()) && "receipts/alt.pdf".equals(arg.getName())));
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

    @Test
    void acknowledgesGcsNotificationHandshake() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/")
                .param("__GCP_CloudEventsMode", "GCS_NOTIFICATION"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());

        verifyNoInteractions(receiptParsingHandler);
    }
}
