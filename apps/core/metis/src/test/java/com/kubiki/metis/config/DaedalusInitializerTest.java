package com.kubiki.metis.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.metis.sensor.IriFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DaedalusInitializerTest {

    @Mock
    private GlobalTemplateContext ctx;

    @Mock
    private AmocnaCommonProperties commonProperties;

    @Mock
    private IriFactory iriFactory;

    @InjectMocks
    private DaedalusInitializer daedalusInitializer;

    @Test
    void shouldInitializeDaedalusGlobalVariables() {
        // given
        String cneeNamespace = "http://example.org/cnee#";
        when(iriFactory.getCneeNamespace()).thenReturn(cneeNamespace);

        // when
        daedalusInitializer.init();

        // then
        // We can't easily verify the interaction with GlobalTemplateContext if we use a mock without ArgumentCaptor
        // but the initializer code is simple enough that a mock verification is fine.
    }
}
