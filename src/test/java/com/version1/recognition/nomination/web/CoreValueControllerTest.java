package com.version1.recognition.nomination.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.version1.recognition.nomination.model.CoreValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoreValueController.class)
class CoreValueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsEverySixCoreValuesWithLabelAndPrompt() throws Exception {
        mockMvc.perform(get("/api/core-values"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(CoreValue.values().length))
                .andExpect(jsonPath("$[0].value").exists())
                .andExpect(jsonPath("$[0].label").exists())
                .andExpect(jsonPath("$[0].prompt").exists());
    }
}
