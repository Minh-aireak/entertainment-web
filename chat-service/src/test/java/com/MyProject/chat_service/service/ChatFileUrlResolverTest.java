package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.response.FileResponse;
import com.MyProject.chat_service.repository.httpclient.FileClient;
import com.MyProject.common.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatFileUrlResolverTest {

    @Mock
    FileClient fileClient;

    ChatFileUrlResolver resolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        resolver = new ChatFileUrlResolver(fileClient);
    }

    @Test
    void resolve_nullFileId_returnsNullWithoutCallingFileClient() {
        assertThat(resolver.resolve(null)).isNull();
        verifyNoInteractions(fileClient);
    }

    @Test
    void resolve_blankFileId_returnsNullWithoutCallingFileClient() {
        assertThat(resolver.resolve("  ")).isNull();
        verifyNoInteractions(fileClient);
    }

    @Test
    void resolve_happyPath_returnsUrlFromFileClient() {
        when(fileClient.getFileInfo("file-1")).thenReturn(
                ApiResponse.<FileResponse>builder().result(FileResponse.builder().url("https://cdn/file-1").build()).build());

        assertThat(resolver.resolve("file-1")).isEqualTo("https://cdn/file-1");
    }

    @Test
    void resolve_fileClientThrows_returnsNullInsteadOfPropagating() {
        when(fileClient.getFileInfo("file-1")).thenThrow(new RuntimeException("file-service down"));

        assertThat(resolver.resolve("file-1")).isNull();
    }

    @Test
    void resolveBatch_mixOfValidAndBlankIds_resolvesOnlyValidOnesAndDeduplicates() {
        when(fileClient.getFileInfo("file-1")).thenReturn(
                ApiResponse.<FileResponse>builder().result(FileResponse.builder().url("https://cdn/file-1").build()).build());
        when(fileClient.getFileInfo("file-2")).thenReturn(
                ApiResponse.<FileResponse>builder().result(FileResponse.builder().url("https://cdn/file-2").build()).build());

        Map<String, String> result = resolver.resolveBatch(Arrays.asList("file-1", "file-1", null, "", "file-2"));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "file-1", "https://cdn/file-1",
                "file-2", "https://cdn/file-2"
        ));
    }

    @Test
    void resolveBatch_emptyCollection_returnsEmptyMap() {
        assertThat(resolver.resolveBatch(Arrays.asList())).isEmpty();
        verifyNoInteractions(fileClient);
    }
}
